package com.assetdashboard.evidence.tool;

import com.assetdashboard.evidence.calculation.AssetEvidenceResponse;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** AssetEvidenceResponse의 구조화된 필드만 채점용 fact token으로 변환한다. */
@Component
public class AssetEvidenceFactExtractor {

  public Set<String> extract(AssetEvidenceResponse response) {
    LinkedHashSet<String> facts = new LinkedHashSet<>();
    facts.add("traceId");

    AssetEvidenceResponse.AssetCalculation calculation = response.calculation();
    addPresence(facts, "quantity", calculation.quantity());
    addPresence(facts, "currentPrice", response.priceEvidence().value());
    addPresence(facts, "exchangeRate", response.exchangeRateEvidence().value());
    addPresence(facts, "valuationKrw", calculation.valuationKrw());
    addPresence(facts, "unrealizedPnlKrw", calculation.unrealizedPnlKrw());
    if (calculation.valuationRule() != null) {
      facts.add("valuationRule");
    }
    if (calculation.unrealizedPnlRule() != null) {
      facts.add("unrealizedPnlRule");
    }
    if (response.priceEvidence().updatedAt() != null) {
      facts.add("priceUpdatedAt");
    }
    if (response.exchangeRateEvidence().updatedAt() != null) {
      facts.add("exchangeRateUpdatedAt");
    }

    response.warnings().forEach(warning -> facts.add(warning.code()));
    facts.add("totalCount");
    facts.add("truncated");
    if (!response.transactions().items().isEmpty()) {
      facts.add("transactionId");
      facts.add("tradedAt");
    }
    return Set.copyOf(facts);
  }

  private void addPresence(Set<String> facts, String name, Object value) {
    facts.add(value == null ? name + "=null" : name);
  }
}
