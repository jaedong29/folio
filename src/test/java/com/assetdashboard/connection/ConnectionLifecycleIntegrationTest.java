package com.assetdashboard.connection;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.assetdashboard.domain.user.dto.DeleteAccountRequest;
import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.domain.user.service.UserAccountService;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.connections.enabled=true",
    "app.connections.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "app.connections.worker-initial-delay-millis=3600000", "app.price.external-enabled=false",
    "app.news.external-enabled=false", "app.ai.enabled=false"})
@AutoConfigureMockMvc
class ConnectionLifecycleIntegrationTest {
  @Autowired ConnectionLifecycleService lifecycle;
  @Autowired ConnectedPortfolioService portfolio;
  @Autowired AccountConnectionRepository connections;
  @Autowired UserRepository users;
  @Autowired UserAccountService accounts;
  @Autowired PasswordEncoder encoder;
  @Autowired MockMvc mvc;
  Long userId;
  private final ConnectionCredentials credentials = new ConnectionCredentials("fixture-key", "fixture-secret");

  @BeforeEach void setup() {
    userId = users.saveAndFlush(User.create(UUID.randomUUID() + "@example.com", encoder.encode("fixture-password"), "QA")).getId();
  }
  @AfterEach void cleanup() {
    connections.deleteAllByUserId(userId);
    users.deleteById(userId);
  }
  private Long connect(ConnectionProvider provider) {
    lifecycle.connect(userId, provider, credentials);
    return connections.findByUserIdAndProvider(userId, provider).orElseThrow().getId();
  }
  private ConnectionProviderClient.Result result(BigDecimal value) {
    var holding = new ConnectedHolding("BTC", "BTC", "CRYPTO", "USDT", BigDecimal.ONE,
        BigDecimal.ZERO, null, null, null, null, null, null, null, value, false);
    return new ConnectionProviderClient.Result(null, new ConnectionSnapshot(Instant.now(), List.of(holding)));
  }
  @Test void failedSyncRetainsLastSnapshotAndMarksItStale() {
    Long id = connect(ConnectionProvider.BINANCE_SPOT);
    var claim = lifecycle.claimNext().orElseThrow();
    lifecycle.complete(claim, result(new BigDecimal("100000")));
    // A duplicate completion cannot change a published snapshot.
    lifecycle.complete(claim, result(BigDecimal.ZERO));
    AccountConnection c = connections.findById(id).orElseThrow();
    c.queue(Instant.now()); connections.saveAndFlush(c);
    var next = lifecycle.claimNext().orElseThrow();
    lifecycle.fail(next, "PROVIDER_UNAVAILABLE");
    var overview = portfolio.overview(userId);
    assertThat(overview.totalValueKRW()).isEqualByComparingTo("100000");
    assertThat(overview.stale()).isTrue();
    assertThat(overview.connections().get(0).status()).isEqualTo(AccountConnection.Status.ERROR);
  }
  @Test void pendingOrUnknownAssetsPreventAnApparentlyCompleteTotal() {
    connect(ConnectionProvider.BINANCE_SPOT);
    var claim = lifecycle.claimNext().orElseThrow();
    lifecycle.complete(claim, result(new BigDecimal("1000")));
    connect(ConnectionProvider.TOSS);
    assertThat(portfolio.overview(userId).totalValueKRW()).isNull();
    assertThat(portfolio.overview(userId).knownValueKRW()).isEqualByComparingTo("1000");
    lifecycle.complete(lifecycle.claimNext().orElseThrow(), result(null));
    var view = portfolio.overview(userId);
    assertThat(view.totalValueKRW()).isNull();
    assertThat(view.unvaluedHoldingCount()).isEqualTo(1);
    assertThat(view.allocation()).allSatisfy(a -> assertThat(a.percentage()).isNull());
  }
  @Test void deletionRejectsOtherUsersAndLateResponsesCannotResurrectAConnection() {
    Long id = connect(ConnectionProvider.BINANCE_SPOT);
    var claim = lifecycle.claimNext().orElseThrow();
    assertThatThrownBy(() -> lifecycle.disconnect(userId + 100000, id))
        .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONNECTION_NOT_FOUND));
    lifecycle.disconnect(userId, id);
    lifecycle.complete(claim, result(BigDecimal.TEN));
    assertThat(connections.findById(id)).isEmpty();
  }
  @Test void simultaneousWorkersOnlyClaimAConnectionOnce() throws Exception {
    connect(ConnectionProvider.TOSS);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Callable<Boolean> task = () -> { start.await(); return lifecycle.claimNext().isPresent(); };
      Future<Boolean> first = executor.submit(task), second = executor.submit(task);
      start.countDown();
      assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    } finally { executor.shutdownNow(); }
  }
  @Test void repeatedSyncIsThrottledAndDisconnectStillWorks() {
    Long id = connect(ConnectionProvider.TOSS);
    var claim = lifecycle.claimNext().orElseThrow();
    assertThatThrownBy(() -> lifecycle.queue(userId, id))
        .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONNECTION_SYNC_THROTTLED));
    lifecycle.complete(claim, result(BigDecimal.TEN));
    assertThatThrownBy(() -> lifecycle.queue(userId, id)).isInstanceOf(BusinessException.class);
    lifecycle.disconnect(userId, id);
  }
  @Test void accountDeletionRemovesStoredKeysAndSnapshots() {
    connect(ConnectionProvider.TOSS);
    accounts.deleteAccount(userId, new DeleteAccountRequest("fixture-password", "DELETE"));
    assertThat(connections.findAllByUserIdOrderById(userId)).isEmpty();
  }
  @Test void apiRequiresAuthenticationAndNeverSerializesSecrets() throws Exception {
    connect(ConnectionProvider.TOSS);
    mvc.perform(get("/api/connections")).andExpect(status().isUnauthorized());
    var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    String body = mvc.perform(get("/api/connections").with(authentication(auth)))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain("fixture-key", "fixture-secret", "encryptedCredentials", "externalAccountId");
    assertThat(connections.findAllByUserIdOrderById(userId).get(0).getEncryptedCredentials()).doesNotContain("fixture");
  }
  @Test void applicationDocumentsBlockThirdPartyAndInlineScripts() throws Exception {
    for (String path : List.of("/", "/index.html")) {
      var response = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse();
      String policy = response.getHeader("Content-Security-Policy");
      assertThat(policy).contains("script-src 'self';", "script-src-attr 'none';",
          "connect-src 'self';", "form-action 'self';", "frame-ancestors 'none'");
      assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }
  }
}
