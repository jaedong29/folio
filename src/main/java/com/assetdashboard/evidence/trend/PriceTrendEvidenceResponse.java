package com.assetdashboard.evidence.trend;

import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** LLM이 가격 방향을 추측하지 않도록 서버가 계산한 시계열 근거 계약. */
public record PriceTrendEvidenceResponse(
    String traceId,
    LocalDateTime generatedAt,
    EvidenceConclusion conclusion,
    Long assetId,
    AssetType assetType,
    String symbol,
    String displaySymbol,
    String window,
    int pointCount,
    Instant startAt,
    Instant endAt,
    BigDecimal startPrice,
    BigDecimal endPrice,
    BigDecimal returnRatePercent,
    BigDecimal directionThresholdPercent,
    PriceTrendDirection direction,
    String directionRule,
    String source,
    LocalDateTime fetchedAt,
    boolean stale,
    List<TrendWarning> warnings) {

  public PriceTrendEvidenceResponse {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  public record TrendWarning(String code, String message) {}
}
