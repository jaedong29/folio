package com.assetdashboard.evidence.trend;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 가격 방향 Tool 응답의 구조화된 필드만 채점용 fact token으로 변환한다. */
@Component
public class PriceTrendEvidenceFactExtractor {

  public Set<String> extract(PriceTrendEvidenceResponse response) {
    LinkedHashSet<String> facts = new LinkedHashSet<>();
    facts.add("trendTraceId");
    facts.add("pointCount");
    facts.add("directionThresholdPercent");
    facts.add("directionRule");
    addPresence(facts, "startPrice", response.startPrice());
    addPresence(facts, "endPrice", response.endPrice());
    addPresence(facts, "returnRatePercent", response.returnRatePercent());
    if (response.direction() == PriceTrendDirection.UNAVAILABLE) {
      facts.add("direction=UNAVAILABLE");
    } else {
      facts.add("PRICE_TREND_AVAILABLE");
      facts.add("direction");
      facts.add("direction=" + response.direction());
      facts.add("priceHistoryAsOf");
    }
    if (response.source() != null) {
      facts.add("priceHistorySource");
    }
    response.warnings().forEach(warning -> facts.add(warning.code()));
    return Set.copyOf(facts);
  }

  private void addPresence(Set<String> facts, String name, Object value) {
    facts.add(value == null ? name + "=null" : name);
  }
}
