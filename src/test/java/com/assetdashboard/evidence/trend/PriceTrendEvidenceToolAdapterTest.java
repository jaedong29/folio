package com.assetdashboard.evidence.trend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceTrendEvidenceToolAdapterTest {

  @Test
  void mapsOnlyStructuredTrendFieldsToFactsAndSafeReferences() {
    PriceTrendEvidenceService service = mock(PriceTrendEvidenceService.class);
    PriceTrendEvidenceResponse payload =
        new PriceTrendEvidenceResponse(
            "550e8400-e29b-41d4-a716-446655440000",
            LocalDateTime.of(2026, 9, 3, 12, 0),
            EvidenceConclusion.CONFIRMED,
            42L,
            AssetType.CRYPTO,
            "ZEC",
            "ZEC",
            "RECENT_7_DAILY_POINTS",
            7,
            Instant.ofEpochMilli(1L),
            Instant.ofEpochMilli(2L),
            new BigDecimal("800"),
            new BigDecimal("833.39"),
            new BigDecimal("4.17"),
            new BigDecimal("2.00"),
            PriceTrendDirection.UP,
            "rule",
            "Binance Spot · 1D",
            LocalDateTime.of(2026, 9, 3, 11, 59),
            false,
            List.of());
    when(service.getPriceTrendEvidence(7L, 42L)).thenReturn(payload);
    PriceTrendEvidenceToolAdapter adapter =
        new PriceTrendEvidenceToolAdapter(service, new PriceTrendEvidenceFactExtractor());

    PriceTrendEvidenceToolResult result = adapter.execute(7L, 42L);

    assertThat(result.grounding().conclusion()).isEqualTo(EvidenceConclusion.CONFIRMED);
    assertThat(result.grounding().evidenceFacts())
        .contains(
            "PRICE_TREND_AVAILABLE",
            "direction",
            "direction=UP",
            "returnRatePercent",
            "directionRule");
    assertThat(result.grounding().referenceIds())
        .containsExactly(
            "asset:42", "price-trend:550e8400-e29b-41d4-a716-446655440000");
  }
}
