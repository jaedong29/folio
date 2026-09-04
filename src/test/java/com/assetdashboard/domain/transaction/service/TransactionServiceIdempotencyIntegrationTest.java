package com.assetdashboard.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.domain.transaction.dto.ExchangeRateMode;
import com.assetdashboard.domain.transaction.dto.TradeRequest;
import com.assetdashboard.domain.transaction.dto.TransactionResponse;
import com.assetdashboard.domain.transaction.repository.IdempotencyKeyRepository;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 재시도로 같은 매수 요청이 실제로 두 번 체결되지 않는지 확인한다.
 *
 * <p>일부러 클래스 단위 {@code @Transactional}을 쓰지 않는다. {@code buy()} 두 번 호출을 테스트 트랜잭션
 * 하나로 감싸면 두 호출이 같은 세션 안에서 서로의 미커밋 쓰기를 그대로 보게 되어, "정말 커밋까지 끝난 뒤에도
 * 중복 실행이 안 되는지"를 검증하지 못한다.
 */
@SpringBootTest
class TransactionServiceIdempotencyIntegrationTest {

  @Autowired private TransactionService transactionService;
  @Autowired private AssetRepository assetRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private IdempotencyKeyRepository idempotencyKeyRepository;

  private Long userId;

  @AfterEach
  void cleanUp() {
    if (userId != null) {
      idempotencyKeyRepository.deleteAllByUserId(userId);
    }
  }

  @Test
  void retryingBuyWithTheSameIdempotencyKeyExecutesExactlyOnce() {
    Fixture fixture = setUpUserWithAssets();
    TradeRequest request = tradeRequest(fixture.settlementId());
    String idempotencyKey = "integration-key-" + UUID.randomUUID();

    TransactionResponse first =
        transactionService.buy(fixture.userId(), fixture.assetId(), request, idempotencyKey);
    TransactionResponse second =
        transactionService.buy(fixture.userId(), fixture.assetId(), request, idempotencyKey);

    assertThat(second.transactionId()).isEqualTo(first.transactionId());
    assertThat(
            transactionRepository.findAllByAssetIdOrderByTradedAtAscIdAsc(fixture.assetId()))
        .hasSize(1);
    assertThat(assetRepository.findById(fixture.assetId()).orElseThrow().getQuantity())
        .isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void reusingTheSameKeyWithADifferentRequestIsRejectedAndDoesNotCreateASecondTransaction() {
    Fixture fixture = setUpUserWithAssets();
    String idempotencyKey = "integration-key-" + UUID.randomUUID();
    transactionService.buy(
        fixture.userId(), fixture.assetId(), tradeRequest(fixture.settlementId()), idempotencyKey);

    TradeRequest changedRequest =
        new TradeRequest(
            new BigDecimal("2"),
            new BigDecimal("1000"),
            null,
            ExchangeRateMode.AUTO,
            fixture.settlementId(),
            "idempotency 통합 테스트 - 다른 수량",
            LocalDateTime.now().minusMinutes(1));

    assertThatThrownBy(
            () ->
                transactionService.buy(
                    fixture.userId(), fixture.assetId(), changedRequest, idempotencyKey))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    assertThat(
            transactionRepository.findAllByAssetIdOrderByTradedAtAscIdAsc(fixture.assetId()))
        .hasSize(1);
  }

  @Test
  void distinctIdempotencyKeysWithTheSameRequestBothExecute() {
    Fixture fixture = setUpUserWithAssets();
    TradeRequest request = tradeRequest(fixture.settlementId());

    transactionService.buy(
        fixture.userId(), fixture.assetId(), request, "integration-key-" + UUID.randomUUID());
    transactionService.buy(
        fixture.userId(), fixture.assetId(), request, "integration-key-" + UUID.randomUUID());

    assertThat(
            transactionRepository.findAllByAssetIdOrderByTradedAtAscIdAsc(fixture.assetId()))
        .hasSize(2);
    assertThat(assetRepository.findById(fixture.assetId()).orElseThrow().getQuantity())
        .isEqualByComparingTo(new BigDecimal("2"));
  }

  private Fixture setUpUserWithAssets() {
    User user =
        userRepository.save(
            User.create("idem-test-" + UUID.randomUUID() + "@example.com", "encoded", "idem"));
    userId = user.getId();

    Asset settlement = Asset.create(userId, AssetType.CASH, "KRW", "원화 대기자금", "KRW");
    settlement.deposit(new BigDecimal("1000000"));
    Asset savedSettlement = assetRepository.save(settlement);

    Asset investment = Asset.create(userId, AssetType.STOCK, "IDEMTEST", "테스트 종목", "KRW");
    Asset savedInvestment = assetRepository.save(investment);

    return new Fixture(userId, savedInvestment.getId(), savedSettlement.getId());
  }

  private TradeRequest tradeRequest(Long settlementId) {
    return new TradeRequest(
        BigDecimal.ONE,
        new BigDecimal("1000"),
        null,
        ExchangeRateMode.AUTO,
        settlementId,
        "idempotency 통합 테스트",
        LocalDateTime.now().minusMinutes(1));
  }

  private record Fixture(Long userId, Long assetId, Long settlementId) {}
}
