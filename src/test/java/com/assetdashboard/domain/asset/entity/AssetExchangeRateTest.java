package com.assetdashboard.domain.asset.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AssetExchangeRateTest {

  @Test
  void buyUsesHistoricalExchangeRateAndValuationUsesCurrentExchangeRate() {
    Asset asset = Asset.create(1L, AssetType.STOCK, "NVDA", "엔비디아", "USD");
    asset.buy(new BigDecimal("3"), new BigDecimal("200"), new BigDecimal("1520"));
    asset.updateCurrentPrice(new BigDecimal("218"), AssetSource.MANUAL);

    assertEquals(new BigDecimal("304000.00000000"), asset.getAvgPrice());
    assertTrue(asset.isValuationBlockedByExchangeRate());
    assertNull(asset.getValuation());
    assertNull(asset.getUnrealizedPnl());

    asset.updateExchangeRate(new BigDecimal("1400"));

    assertFalse(asset.isValuationBlockedByExchangeRate());
    assertEquals(new BigDecimal("915600.00"), asset.getValuation());
    assertEquals(new BigDecimal("3600.00"), asset.getUnrealizedPnl());
    assertEquals(new BigDecimal("0.39"), asset.getPnlRate());
  }

  @Test
  void krwAssetKeepsExchangeRateOne() {
    Asset cash = Asset.create(1L, AssetType.CASH, "KRW", "현금", "KRW");
    cash.deposit(new BigDecimal("100000"));

    assertFalse(cash.isValuationBlockedByExchangeRate());
    assertEquals(BigDecimal.ONE, cash.getCurrentExchangeRate());
    assertEquals(new BigDecimal("100000.00"), cash.getValuation());
  }

  @Test
  void zeroForeignCashIsExactlyZeroWithoutExchangeRate() {
    Asset usdt = Asset.create(1L, AssetType.CASH, "USDT", "테더 대기자금", "USDT");

    assertTrue(usdt.isDefaultSettlementAsset());
    assertFalse(usdt.isValuationBlockedByExchangeRate());
    assertEquals(new BigDecimal("0.00"), usdt.getValuation());
  }

  @Test
  void realForeignExchangeRateOneIsNotTreatedAsMissing() {
    Asset usd = Asset.create(1L, AssetType.CASH, "USD", "달러 대기자금", "USD");
    usd.deposit(BigDecimal.TEN);
    usd.updateExchangeRate(BigDecimal.ONE);

    assertFalse(usd.isValuationBlockedByExchangeRate());
    assertEquals(new BigDecimal("10.00"), usd.getValuation());
  }
}
