package com.assetdashboard.evidence.trend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.infra.price.history.PriceHistoryPoint;
import com.assetdashboard.infra.price.history.PriceHistoryQueryService;
import com.assetdashboard.infra.price.history.PriceHistoryQuote;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceTrendEvidenceServiceTest {

  private final AssetService assetService = mock(AssetService.class);
  private final PriceHistoryQueryService historyQueryService =
      mock(PriceHistoryQueryService.class);
  private final PriceTrendEvidenceService service =
      new PriceTrendEvidenceService(assetService, historyQueryService);

  @Test
  void calculatesUpDirectionFromPriceHistoryInsteadOfAveragePurchasePrice() {
    Asset asset = ownedZec();
    LocalDateTime fetchedAt = LocalDateTime.of(2026, 9, 3, 12, 0);
    when(assetService.getOwnedAsset(7L, 42L)).thenReturn(asset);
    when(historyQueryService.getHistory(AssetType.CRYPTO, "ZEC"))
        .thenReturn(
            new PriceHistoryQuote(
                "Binance Spot · 1D",
                fetchedAt,
                false,
                List.of(
                    new PriceHistoryPoint(1_788_220_800_000L, new BigDecimal("800")),
                    new PriceHistoryPoint(1_788_307_200_000L, new BigDecimal("820")),
                    new PriceHistoryPoint(1_788_393_600_000L, new BigDecimal("833.39")))));

    PriceTrendEvidenceResponse result = service.getPriceTrendEvidence(7L, 42L);

    assertThat(result.conclusion()).isEqualTo(EvidenceConclusion.CONFIRMED);
    assertThat(result.direction()).isEqualTo(PriceTrendDirection.UP);
    assertThat(result.returnRatePercent()).isEqualByComparingTo("4.17");
    assertThat(result.startPrice()).isEqualByComparingTo("800");
    assertThat(result.endPrice()).isEqualByComparingTo("833.39");
    assertThat(result.directionThresholdPercent()).isEqualByComparingTo("2.00");
  }

  @Test
  void preservesUnavailableWhenFewerThanTwoPricePointsExist() {
    Asset asset = ownedZec();
    when(assetService.getOwnedAsset(7L, 42L)).thenReturn(asset);
    when(historyQueryService.getHistory(AssetType.CRYPTO, "ZEC"))
        .thenReturn(
            new PriceHistoryQuote(
                "Binance Spot · 1D", null, true, List.of()));

    PriceTrendEvidenceResponse result = service.getPriceTrendEvidence(7L, 42L);

    assertThat(result.conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(result.direction()).isEqualTo(PriceTrendDirection.UNAVAILABLE);
    assertThat(result.returnRatePercent()).isNull();
    assertThat(result.warnings())
        .extracting(PriceTrendEvidenceResponse.TrendWarning::code)
        .containsExactly("PRICE_HISTORY_MISSING");
  }

  @Test
  void treatsExactlyTwoPercentAsFlatByThePublishedRule() {
    Asset asset = ownedZec();
    when(assetService.getOwnedAsset(7L, 42L)).thenReturn(asset);
    when(historyQueryService.getHistory(AssetType.CRYPTO, "ZEC"))
        .thenReturn(
            new PriceHistoryQuote(
                "Binance Spot · 1D",
                LocalDateTime.of(2026, 9, 3, 12, 0),
                false,
                List.of(
                    new PriceHistoryPoint(1L, new BigDecimal("100")),
                    new PriceHistoryPoint(2L, new BigDecimal("102")))));

    assertThat(service.getPriceTrendEvidence(7L, 42L).direction())
        .isEqualTo(PriceTrendDirection.FLAT);
  }

  private Asset ownedZec() {
    Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(42L);
    when(asset.getType()).thenReturn(AssetType.CRYPTO);
    when(asset.getSymbol()).thenReturn("ZEC");
    when(asset.getDisplaySymbol()).thenReturn("ZEC");
    return asset;
  }
}
