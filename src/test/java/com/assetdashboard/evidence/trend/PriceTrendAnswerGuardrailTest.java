package com.assetdashboard.evidence.trend;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceTrendAnswerGuardrailTest {

  private final PriceTrendAnswerGuardrail guardrail = new PriceTrendAnswerGuardrail();

  @Test
  void replacesTheObservedPortfolioProfitAsTrendFailure() {
    String unsafe =
        "ZEC의 현재 방향성은 상승세입니다. 평가손익률 +104.43%로 큰 폭의 상승입니다.";

    PriceTrendAnswerGuardrail.GuardrailDecision decision =
        guardrail.apply(unsafe, available(PriceTrendDirection.UP));

    assertThat(decision.replaced()).isTrue();
    assertThat(decision.violationCode()).isEqualTo("PORTFOLIO_RETURN_USED_AS_TREND");
    assertThat(decision.answer())
        .contains("최근 3개 일별 가격", "800에서 833.39", "4.17%", "상승 방향")
        .doesNotContain("104.43");
  }

  @Test
  void preservesGroundedTrendAnswer() {
    String grounded = "최근 일별 가격이 800에서 833.39로 변해 7일 기준 상승 방향입니다.";

    PriceTrendAnswerGuardrail.GuardrailDecision decision =
        guardrail.apply(grounded, available(PriceTrendDirection.UP));

    assertThat(decision.replaced()).isFalse();
    assertThat(decision.answer()).isEqualTo(grounded);
  }

  private PriceTrendEvidenceResponse available(PriceTrendDirection direction) {
    return new PriceTrendEvidenceResponse(
        "550e8400-e29b-41d4-a716-446655440000",
        LocalDateTime.of(2026, 9, 3, 12, 0),
        EvidenceConclusion.CONFIRMED,
        42L,
        AssetType.CRYPTO,
        "ZEC",
        "ZEC",
        "RECENT_7_DAILY_POINTS",
        3,
        Instant.ofEpochMilli(1L),
        Instant.ofEpochMilli(2L),
        new BigDecimal("800"),
        new BigDecimal("833.39"),
        new BigDecimal("4.17"),
        new BigDecimal("2.00"),
        direction,
        "rule",
        "Binance Spot · 1D",
        LocalDateTime.of(2026, 9, 3, 12, 0),
        false,
        List.of());
  }
}
