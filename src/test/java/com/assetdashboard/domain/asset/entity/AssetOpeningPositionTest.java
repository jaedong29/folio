package com.assetdashboard.domain.asset.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetOpeningPositionTest {

  @Test
  void unknownAveragePriceKeepsValuationButHidesProfit() {
    Asset asset = Asset.create(1L, AssetType.CRYPTO, "ZEC", "Zcash", "USDT");
    asset.initializePosition(new BigDecimal("14.7"), null, null);
    asset.updateCurrentPrice(new BigDecimal("508.6"), AssetSource.API);
    asset.updateExchangeRate(new BigDecimal("1412"));

    assertThat(asset.getValuation()).isEqualByComparingTo("10556705.04");
    assertThat(asset.getCost()).isNull();
    assertThat(asset.getUnrealizedPnl()).isNull();
    assertThat(asset.getPnlRate()).isNull();
  }

  @Test
  void knownAveragePriceUsesHistoricalFxForKrwCost() {
    Asset asset = Asset.create(1L, AssetType.CRYPTO, "ZEC", "Zcash", "USDT");
    asset.initializePosition(
        new BigDecimal("14.7"), new BigDecimal("370.4"), new BigDecimal("1380"));
    asset.updateCurrentPrice(new BigDecimal("508.6"), AssetSource.API);
    asset.updateExchangeRate(new BigDecimal("1412"));

    assertThat(asset.getAvgPriceOriginal()).isEqualByComparingTo("370.4");
    assertThat(asset.getAvgPrice()).isEqualByComparingTo("511152");
    assertThat(asset.getUnrealizedPnl()).isEqualByComparingTo("3042770.64");
    assertThat(asset.getPnlRate()).isEqualByComparingTo("40.50");
  }

  @Test
  void replayStartsFromOpeningPositionInsteadOfZero() {
    Asset asset = Asset.create(1L, AssetType.STOCK, "MU", "마이크론", "USD");
    asset.initializePosition(
        new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("1400"));

    Transaction buy =
        Transaction.createBuy(
            1L,
            new BigDecimal("2"),
            new BigDecimal("120"),
            new BigDecimal("1400"),
            null,
            null,
            null,
            LocalDateTime.of(2026, 8, 1, 10, 0));
    Transaction sell =
        Transaction.createSell(
            1L,
            BigDecimal.ONE,
            new BigDecimal("130"),
            new BigDecimal("1400"),
            null,
            null,
            null,
            LocalDateTime.of(2026, 8, 2, 10, 0));

    asset.replay(List.of(buy, sell));

    assertThat(asset.getQuantity()).isEqualByComparingTo("11");
    assertThat(asset.getAvgPrice()).isEqualByComparingTo("144666.66666667");
    assertThat(asset.getRealizedPnl()).isEqualByComparingTo("37333.33333333");
  }

  @Test
  void sellingUnknownCostPositionDoesNotInventProfitFromZeroCost() {
    Asset asset = Asset.create(1L, AssetType.CRYPTO, "LTC", "Litecoin", "USDT");
    asset.initializePosition(new BigDecimal("2"), null, null);

    asset.sell(BigDecimal.ONE, new BigDecimal("100"), new BigDecimal("1400"));

    assertThat(asset.getQuantity()).isEqualByComparingTo("1");
    assertThat(asset.getRealizedPnl()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void correctsOpeningQuantityWithoutCreatingSaleProfit() {
    Asset asset = Asset.create(1L, AssetType.CRYPTO, "ZEC", "Zcash", "USDT");
    asset.initializePosition(
        new BigDecimal("15.7"), new BigDecimal("370.4"), new BigDecimal("1380"));

    asset.correctCurrentQuantity(new BigDecimal("14.7"), List.of());

    assertThat(asset.getQuantity()).isEqualByComparingTo("14.7");
    assertThat(asset.getAvgPriceOriginal()).isEqualByComparingTo("370.4");
    assertThat(asset.getRealizedPnl()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(asset.getPositionCorrectedAt()).isNotNull();
  }

  @Test
  void quantityCorrectionReplaysLaterTradesFromCorrectedOpeningPosition() {
    Asset asset = Asset.create(1L, AssetType.STOCK, "MU", "마이크론", "USD");
    asset.initializePosition(
        new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("1400"));
    Transaction buy =
        Transaction.createBuy(
            1L,
            new BigDecimal("2"),
            new BigDecimal("120"),
            new BigDecimal("1400"),
            null,
            null,
            null,
            LocalDateTime.of(2026, 8, 1, 10, 0));
    Transaction sell =
        Transaction.createSell(
            1L,
            BigDecimal.ONE,
            new BigDecimal("130"),
            new BigDecimal("1400"),
            null,
            null,
            null,
            LocalDateTime.of(2026, 8, 2, 10, 0));
    List<Transaction> history = List.of(buy, sell);
    asset.replay(history);

    asset.correctCurrentQuantity(new BigDecimal("10"), history);

    assertThat(asset.getQuantity()).isEqualByComparingTo("10");
    assertThat(asset.getAvgPrice()).isEqualByComparingTo("145090.90909091");
    assertThat(asset.getRealizedPnl()).isEqualByComparingTo("36909.09090909");
  }

  @Test
  void correctionThatBreaksPastSellIsRejectedWithoutChangingCurrentState() {
    Asset asset = Asset.create(1L, AssetType.STOCK, "MU", "마이크론", "USD");
    asset.initializePosition(
        BigDecimal.ONE, new BigDecimal("100"), new BigDecimal("1400"));
    Transaction sell =
        Transaction.createSell(
            1L,
            BigDecimal.ONE,
            new BigDecimal("110"),
            new BigDecimal("1400"),
            null,
            null,
            null,
            LocalDateTime.of(2026, 8, 1, 10, 0));
    Transaction buy =
        Transaction.createBuy(
            1L,
            new BigDecimal("5"),
            new BigDecimal("120"),
            new BigDecimal("1400"),
            null,
            null,
            null,
            LocalDateTime.of(2026, 8, 2, 10, 0));
    List<Transaction> history = List.of(sell, buy);
    asset.replay(history);

    assertThatThrownBy(() -> asset.correctCurrentQuantity(new BigDecimal("4"), history))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getMessage()).contains("기존 매도 시점", "거래 내역"));
    assertThat(asset.getQuantity()).isEqualByComparingTo("5");
    assertThat(asset.getAvgPrice()).isEqualByComparingTo("168000");
    assertThat(asset.getRealizedPnl()).isEqualByComparingTo("14000");
  }
}
