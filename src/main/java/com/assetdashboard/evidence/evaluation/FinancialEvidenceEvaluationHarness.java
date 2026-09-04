package com.assetdashboard.evidence.evaluation;

import com.assetdashboard.evidence.trace.AgentRunResult;
import com.assetdashboard.evidence.trace.AgentTraceResponse;
import com.assetdashboard.evidence.trace.AgentTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 골든셋 채점과 Trace 저장을 한 경로로 묶는 최소 평가 하네스. */
@Service
@RequiredArgsConstructor
public class FinancialEvidenceEvaluationHarness {

  private final FinancialEvidenceGoldenSetLoader goldenSetLoader;
  private final RuleBasedFinancialEvidenceScorer scorer;
  private final AgentTraceService agentTraceService;

  public AgentTraceResponse evaluateAndRecord(
      Long userId, String caseId, AgentRunResult actual) {
    FinancialEvidenceGoldenCase goldenCase = goldenSetLoader.requireCase(caseId);
    FinancialEvidenceEvaluationResult evaluation = scorer.score(goldenCase, actual);
    return agentTraceService.record(
        userId, goldenCase.id(), goldenCase.question(), actual, evaluation);
  }
}
