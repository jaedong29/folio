package com.assetdashboard.evidence.agent;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Tool 원문 응답에서 애플리케이션 코드가 결정적으로 추출한 채점용 근거. */
public record GroundedToolResult(
    String toolName,
    EvidenceConclusion conclusion,
    Set<String> evidenceFacts,
    List<String> referenceIds) {

  public GroundedToolResult {
    if (toolName == null || toolName.isBlank()) {
      throw new IllegalArgumentException("toolName이 필요합니다.");
    }
    Objects.requireNonNull(conclusion, "Tool 결론이 필요합니다.");
    evidenceFacts = evidenceFacts == null ? Set.of() : Set.copyOf(evidenceFacts);
    referenceIds = referenceIds == null ? List.of() : List.copyOf(referenceIds);
  }
}
