package com.assetdashboard.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.domain.transaction.dto.CashFlowRequest;
import com.assetdashboard.domain.transaction.dto.ExchangeRateMode;
import com.assetdashboard.domain.transaction.dto.TradeRequest;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TransactionServiceTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private TransactionRepository transactionRepository;
  private AssetService assetService;
  private TransactionService service;

  @BeforeEach
  void setUp() {
    transactionRepository = mock(TransactionRepository.class);
    assetService = mock(AssetService.class);
    service = new TransactionService(transactionRepository, assetService);
  }

  @Test
  void pastAutoTradeIsRejectedEvenWhenClientSendsCurrentRate() {
    Fixtures fixtures = fixtures("USDT", BigDecimal.valueOf(1000));
    TradeRequest request =
        tradeRequest(
            new BigDecimal("10"),
            new BigDecimal("1404"),
            ExchangeRateMode.AUTO,
            LocalDateTime.now(KST).minusDays(1));

    assertThatThrownBy(() -> service.buy(1L, fixtures.investment().getId(), request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
              assertThat(error.getMessage()).contains("과거 외화 거래", "직접 입력");
            });
  }

  @Test
  void pastManualTradeUsesProvidedHistoricalRate() {
    Fixtures fixtures = fixtures("USDT", BigDecimal.valueOf(1000));
    TradeRequest request =
        tradeRequest(
            new BigDecimal("10"),
            new BigDecimal("1380"),
            ExchangeRateMode.MANUAL,
            LocalDateTime.now(KST).minusDays(1));

    service.buy(1L, fixtures.investment().getId(), request);

    assertThat(fixtures.investment().getAvgPrice()).isEqualByComparingTo("13800");
    assertThat(fixtures.settlement().getQuantity()).isEqualByComparingTo("990");
  }

  @Test
  void todayAutoTradeUsesStoredCurrentRate() {
    Fixtures fixtures = fixtures("USDT", BigDecimal.valueOf(1000));
    fixtures.investment().updateExchangeRate(new BigDecimal("1404"));
    TradeRequest request =
        tradeRequest(
            new BigDecimal("10"), null, ExchangeRateMode.AUTO, LocalDateTime.now(KST).minusMinutes(1));

    service.buy(1L, fixtures.investment().getId(), request);

    assertThat(fixtures.investment().getAvgPrice()).isEqualByComparingTo("14040");
  }

  @Test
  void autoTradeWithoutStoredCurrentRateFailsClosed() {
    Fixtures fixtures = fixtures("USDT", BigDecimal.valueOf(1000));
    TradeRequest request =
        tradeRequest(
            new BigDecimal("10"), null, ExchangeRateMode.AUTO, LocalDateTime.now(KST).minusMinutes(1));

    assertThatThrownBy(() -> service.buy(1L, fixtures.investment().getId(), request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getMessage()).contains("현재 환율", "MANUAL"));
  }

  @Test
  void insufficientSettlementFundsNamesSettlementAssetAndUsesDedicatedCode() {
    Fixtures fixtures = fixtures("USDT", BigDecimal.ZERO);
    TradeRequest request =
        tradeRequest(
            new BigDecimal("10"),
            new BigDecimal("1400"),
            ExchangeRateMode.MANUAL,
            LocalDateTime.now(KST).minusMinutes(1));

    assertThatThrownBy(() -> service.buy(1L, fixtures.investment().getId(), request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_SETTLEMENT_FUNDS);
              assertThat(error.getMessage()).contains("테더 대기자금(USDT)", "필요: 10 USDT");
            });
    assertThat(fixtures.investment().getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void settlementCurrencyMismatchHasDedicatedCode() {
    Fixtures fixtures = fixtures("USD", BigDecimal.valueOf(1000));
    TradeRequest request =
        tradeRequest(
            new BigDecimal("10"),
            new BigDecimal("1400"),
            ExchangeRateMode.MANUAL,
            LocalDateTime.now(KST).minusMinutes(1));

    assertThatThrownBy(() -> service.buy(1L, fixtures.investment().getId(), request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_CURRENCY_MISMATCH));
  }

  @Test
  void deletingSellWithoutSettlementBalanceUsesDedicatedCodeAndDoesNotDelete() {
    Fixtures fixtures = fixtures("USDT", BigDecimal.ZERO);
    Transaction sell =
        Transaction.createSell(
            fixtures.investment().getId(),
            BigDecimal.ONE,
            new BigDecimal("70000"),
            new BigDecimal("1400"),
            fixtures.settlement().getId(),
            new BigDecimal("70000"),
            "매도",
            LocalDateTime.now(KST).minusMinutes(1));
    when(transactionRepository.findByIdAndAssetId(99L, fixtures.investment().getId()))
        .thenReturn(Optional.of(sell));

    assertThatThrownBy(() -> service.delete(1L, fixtures.investment().getId(), 99L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_SETTLEMENT_FUNDS);
              assertThat(error.getMessage())
                  .contains("테더 대기자금(USDT)", "보유: 0 USDT", "필요: 70000 USDT");
            });

    assertThat(fixtures.settlement().getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    verify(transactionRepository, never()).delete(any(Transaction.class));
  }

  @Test
  void pastForeignCashFlowAlsoRequiresManualRate() {
    Asset cash = asset(30L, AssetType.CASH, "USDT", "테더 대기자금", "USDT");
    when(assetService.getOwnedAsset(1L, 30L)).thenReturn(cash);
    CashFlowRequest request =
        new CashFlowRequest(
            new BigDecimal("100"),
            new BigDecimal("1400"),
            null,
            "과거 입금",
            LocalDateTime.now(KST).minusDays(1));

    assertThatThrownBy(() -> service.deposit(1L, 30L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getMessage()).contains("과거 외화 거래"));
  }

  private Fixtures fixtures(String settlementCurrency, BigDecimal settlementBalance) {
    Asset investment = asset(10L, AssetType.CRYPTO, "BTC", "비트코인", "USDT");
    Asset settlement =
        asset(20L, AssetType.CASH, settlementCurrency, settlementName(settlementCurrency), settlementCurrency);
    if (settlementBalance.signum() > 0) {
      settlement.deposit(settlementBalance);
    }
    when(assetService.getOwnedAsset(1L, 10L)).thenReturn(investment);
    when(assetService.getOwnedAsset(1L, 20L)).thenReturn(settlement);
    return new Fixtures(investment, settlement);
  }

  private TradeRequest tradeRequest(
      BigDecimal price,
      BigDecimal exchangeRate,
      ExchangeRateMode mode,
      LocalDateTime tradedAt) {
    return new TradeRequest(
        BigDecimal.ONE, price, exchangeRate, mode, 20L, "테스트", tradedAt);
  }

  private Asset asset(
      Long id, AssetType type, String symbol, String name, String currency) {
    Asset asset = Asset.create(1L, type, symbol, name, currency);
    ReflectionTestUtils.setField(asset, "id", id);
    return asset;
  }

  private String settlementName(String currency) {
    return "USDT".equals(currency) ? "테더 대기자금" : "달러 대기자금";
  }

  private record Fixtures(Asset investment, Asset settlement) {}
}
