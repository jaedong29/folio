package com.assetdashboard.connection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.assetdashboard.infra.price.fx.FxRateQueryService;
import com.assetdashboard.infra.price.fx.FxRateQuote;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ConnectionProviderClientTest {
  private MockRestServiceServer server;
  private ConnectionProviderClient client;
  private FxRateQueryService fx;
  private final ConnectionCredentials credentials = new ConnectionCredentials("fixture-key", "fixture-secret");
  private static final String PERMISSIONS = """
      {"enableReading":true,"enableWithdrawals":false,"enableInternalTransfer":false,
       "enableMargin":false,"enableFutures":false,"permitsUniversalTransfer":false,
       "enableVanillaOptions":false,"enableFixApiTrade":false,"enableSpotAndMarginTrading":false,
       "enablePortfolioMarginTrading":false}
      """;
  @BeforeEach void setup() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    fx = mock(FxRateQueryService.class);
    client = new ConnectionProviderClient(builder.build(), fx, Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC));
  }
  @AfterEach void verify() { server.verify(); }
  private void permissions(String body) {
    server.expect(requestTo(Matchers.startsWith("https://api.binance.com/sapi/v1/account/apiRestrictions?timestamp=")))
        .andExpect(method(HttpMethod.GET)).andExpect(header("X-MBX-APIKEY", "fixture-key"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
  }
  private void balances(String body) {
    server.expect(requestTo(Matchers.allOf(Matchers.startsWith("https://api.binance.com/api/v3/account?"),
        Matchers.containsString("omitZeroBalances=true&signature="))))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
  }
  @Test void includesLockedSpotBalancesAndLeavesUnknownCostBasisUnknown() {
    permissions(PERMISSIONS);
    balances("""
        {"accountType":"SPOT","balances":[{"asset":"BTC","free":"0.5","locked":"0.2"},
        {"asset":"USDT","free":"100","locked":"20"}]}
        """);
    server.expect(requestTo("https://api.binance.com/api/v3/ticker/price"))
        .andRespond(withSuccess("[{\"symbol\":\"BTCUSDT\",\"price\":\"100000\"}]", MediaType.APPLICATION_JSON));
    when(fx.fetchWithFallback("USDT", false)).thenReturn(Optional.of(FxRateQuote.of("USDT", new BigDecimal("1400"), "fixture")));
    var items = client.fetch(ConnectionProvider.BINANCE_SPOT, credentials, null).snapshot().holdings();
    assertThat(items).hasSize(2);
    assertThat(items.get(0).quantity()).isEqualByComparingTo("0.7");
    assertThat(items.get(0).valuationKRW()).isEqualByComparingTo("98000000");
    assertThat(items.get(0).averagePurchasePrice()).isNull();
    assertThat(items.get(0).providerProfitLoss()).isNull();
    assertThat(items.get(1).valuationKRW()).isEqualByComparingTo("168000");
  }
  @Test void priceAndFxOutagesKeepBalancesWithoutInventingZeroValuation() {
    permissions(PERMISSIONS);
    balances("{\"accountType\":\"SPOT\",\"balances\":[{\"asset\":\"BTC\",\"free\":\"1\",\"locked\":\"0\"}]}");
    server.expect(requestTo("https://api.binance.com/api/v3/ticker/price")).andRespond(withServerError());
    when(fx.fetchWithFallback("USDT", false)).thenReturn(Optional.empty());
    var h = client.fetch(ConnectionProvider.BINANCE_SPOT, credentials, null).snapshot().holdings().get(0);
    assertThat(h.quantity()).isEqualByComparingTo("1");
    assertThat(h.valuationKRW()).isNull();
    assertThat(h.price()).isNull();
  }
  @Test void rejectsTradingKeysBeforeFetchingBalances() {
    permissions(PERMISSIONS.replace("\"enableSpotAndMarginTrading\":false", "\"enableSpotAndMarginTrading\":true"));
    assertThatThrownBy(() -> client.fetch(ConnectionProvider.BINANCE_SPOT, credentials, null))
        .isInstanceOf(ConnectionFetchException.class).hasMessage("READ_ONLY_KEY_REQUIRED");
  }
  @Test void malformedBalancesAreNotAnEmptyPortfolio() {
    permissions(PERMISSIONS); balances("{\"accountType\":\"SPOT\"}");
    assertThatThrownBy(() -> client.fetch(ConnectionProvider.BINANCE_SPOT, credentials, null))
        .hasMessage("INVALID_PROVIDER_RESPONSE");
  }
  @Test void realEmptySpotAccountIsAValidSnapshot() {
    permissions(PERMISSIONS); balances("{\"accountType\":\"SPOT\",\"balances\":[]}");
    assertThat(client.fetch(ConnectionProvider.BINANCE_SPOT, credentials, null).snapshot().holdings()).isEmpty();
    verifyNoInteractions(fx);
  }
  private void tossAuthAndAccount() {
    String token = "fixture-token".repeat(40);
    server.expect(requestTo("https://openapi.tossinvest.com/oauth2/token")).andExpect(method(HttpMethod.POST))
        .andExpect(content().string(Matchers.containsString("grant_type=client_credentials")))
        .andRespond(withSuccess("{\"access_token\":\"" + token + "\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://openapi.tossinvest.com/api/v1/accounts"))
        .andExpect(header("Authorization", "Bearer " + token))
        .andRespond(withSuccess("{\"result\":[{\"accountSeq\":1,\"accountType\":\"BROKERAGE\"}]}", MediaType.APPLICATION_JSON));
  }
  @Test void tossPreservesProviderProfitInOriginalCurrencyAndDoesNotUseCurrentFxAsCostBasis() {
    tossAuthAndAccount();
    server.expect(requestTo("https://openapi.tossinvest.com/api/v1/holdings"))
        .andExpect(header("X-Tossinvest-Account", "1"))
        .andRespond(withSuccess("""
            {"result":{"items":[{"symbol":"AAPL","name":"Apple","marketCountry":"US","currency":"USD",
            "quantity":"10","lastPrice":"178.5","averagePurchasePrice":"155.3",
            "marketValue":{"amount":"1785"},"profitLoss":{"amount":"232"}}]}}
            """, MediaType.APPLICATION_JSON));
    when(fx.fetchWithFallback("USD", false)).thenReturn(Optional.empty());
    var result = client.fetch(ConnectionProvider.TOSS, credentials, null);
    assertThat(result.accountId()).isEqualTo("1");
    var h = result.snapshot().holdings().get(0);
    assertThat(h.providerProfitLoss()).isEqualByComparingTo("232");
    assertThat(h.averagePurchasePrice()).isEqualByComparingTo("155.3");
    assertThat(h.valuationKRW()).isNull();
  }
  @Test void upstreamAuthErrorsNeverExposeCredentialsOrProviderBody() {
    server.expect(requestTo(Matchers.startsWith("https://api.binance.com/sapi/v1/account/apiRestrictions?")))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("fixture-secret upstream body"));
    assertThatThrownBy(() -> client.fetch(ConnectionProvider.BINANCE_SPOT, credentials, null))
        .hasMessage("AUTH_OR_IP_REJECTED").hasNoCause();
  }
}
