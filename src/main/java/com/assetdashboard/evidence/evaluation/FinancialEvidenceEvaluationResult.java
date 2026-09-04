package com.assetdashboard.evidence.evaluation;

import java.util.List;
import java.util.Set;

/** 평균 점수 대신 통과 여부와 구체적인 실패 원인을 남기는 최소 평가 결과. */
public record FinancialEvidenceEvaluationResult(
    String caseId,
    String traceId,
    String scorerVersion,
    boolean passed,
    boolean hardFailure,
    Set<EvaluationFailureCode> failureCodes,
    List<String> failureDetails) {

  public FinancialEvidenceEvaluationResult {
    failureCodes = failureCodes == null ? Set.of() : Set.copyOf(failureCodes);
    failureDetails = failureDetails == null ? List.of() : List.copyOf(failureDetails);
  }
}
