package com.assetdashboard.evidence.evaluation;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.util.List;

/** 질문별 기대 Tool·결론·필수 근거·금지 주장을 고정한 평가 계약. */
public record FinancialEvidenceGoldenCase(
    String id,
    String question,
    List<String> expectedTools,
    EvidenceConclusion expectedConclusion,
    List<String> requiredEvidence,
    List<String> forbiddenClaims,
    String fixture) {

  public FinancialEvidenceGoldenCase {
    expectedTools = expectedTools == null ? List.of() : List.copyOf(expectedTools);
    requiredEvidence = requiredEvidence == null ? List.of() : List.copyOf(requiredEvidence);
    forbiddenClaims = forbiddenClaims == null ? List.of() : List.copyOf(forbiddenClaims);
  }
}
