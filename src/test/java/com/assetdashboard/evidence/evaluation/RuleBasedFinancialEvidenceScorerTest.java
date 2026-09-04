package com.assetdashboard.evidence.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.trace.AgentRunResult;
import com.assetdashboard.evidence.trace.AgentRunStatus;
import com.assetdashboard.evidence.trace.AgentSafetyViolation;
import com.assetdashboard.evidence.trace.AgentTokenUsage;
import com.assetdashboard.evidence.trace.AgentTraceStep;
import com.assetdashboard.evidence.trace.AgentTraceStepStatus;
import com.assetdashboard.evidence.trace.AgentTraceStepType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleBasedFinancialEvidenceScorerTest {

  private final RuleBasedFinancialEvidenceScorer scorer =
      new RuleBasedFinancialEvidenceScorer();

  @Test
  void passesWhenToolConclusionAndEvidenceMatch() {
    FinancialEvidenceEvaluationResult result = scorer.score(missingFxCase(), passingResult());

    assertThat(result.passed()).isTrue();
    assertThat(result.hardFailure()).isFalse();
    assertThat(result.failureCodes()).isEmpty();
  }

  @Test
  void treatsFabricatedFinancialValueAsHardFailure() {
    AgentRunResult passing = passingResult();
    AgentRunResult fabricated =
        new AgentRunResult(
            passing.traceId(),
            passing.promptVersion(),
            passing.model(),
            passing.status(),
            EvidenceConclusion.CONFIRMED,
            "환율은 1로 적용했습니다.",
            passing.steps(),
            passing.evidenceFacts(),
            Set.of(AgentSafetyViolation.FABRICATED_VALUE),
            passing.latencyMs(),
            passing.tokenUsage());

    FinancialEvidenceEvaluationResult result = scorer.score(missingFxCase(), fabricated);

    assertThat(result.passed()).isFalse();
    assertThat(result.hardFailure()).isTrue();
    assertThat(result.failureCodes())
        .contains(
            EvaluationFailureCode.WRONG_CONCLUSION,
            EvaluationFailureCode.SAFETY_VIOLATION);
  }

  @Test
  void reportsModelQualityFailureWhenOutputGuardrailHadToIntervene() {
    AgentRunResult passing = passingResult();
    List<AgentTraceStep> steps = new java.util.ArrayList<>(passing.steps());
    steps.add(
        new AgentTraceStep(
            "guardrail-output",
            "root",
            AgentTraceStepType.GUARDRAIL,
            "priceTrendClaimValidation",
            AgentTraceStepStatus.BLOCKED,
            0,
            "PORTFOLIO_RETURN_USED_AS_TREND",
            List.of()));
    AgentRunResult intervened =
        new AgentRunResult(
            passing.traceId(),
            passing.promptVersion(),
            passing.model(),
            passing.status(),
            passing.conclusion(),
            passing.finalAnswer(),
            steps,
            passing.evidenceFacts(),
            passing.safetyViolations(),
            passing.latencyMs(),
            passing.tokenUsage());

    FinancialEvidenceEvaluationResult result = scorer.score(missingFxCase(), intervened);

    assertThat(result.passed()).isFalse();
    assertThat(result.hardFailure()).isFalse();
    assertThat(result.failureCodes())
        .containsExactly(EvaluationFailureCode.GUARDRAIL_INTERVENED);
  }

  private FinancialEvidenceGoldenCase missingFxCase() {
    return new FinancialEvidenceGoldenCase(
        "missing-fx",
        "현재 환율을 확인할 수 없는 외화 자산이 있어?",
        List.of("getAssetEvidence"),
        EvidenceConclusion.UNAVAILABLE,
        List.of("FX_MISSING", "exchangeRate=null", "valuationKrw=null"),
        List.of("exchangeRate=1", "현재 환율로 추정"),
        "missing-fx");
  }

  private AgentRunResult passingResult() {
    return new AgentRunResult(
        "550e8400-e29b-41d4-a716-446655440000",
        "financial-agent-v1",
        "not-connected-fixture",
        AgentRunStatus.COMPLETED,
        EvidenceConclusion.UNAVAILABLE,
        "현재 환율 근거가 없어 원화 평가금액을 확인할 수 없습니다.",
        List.of(
            new AgentTraceStep(
                "root",
                null,
                AgentTraceStepType.AGENT,
                "financialEvidenceAgent",
                AgentTraceStepStatus.SUCCESS,
                20,
                null,
                List.of()),
            new AgentTraceStep(
                "tool-1",
                "root",
                AgentTraceStepType.TOOL,
                "getAssetEvidence",
                AgentTraceStepStatus.SUCCESS,
                5,
                null,
                List.of("asset:11"))),
        Set.of("FX_MISSING", "exchangeRate=null", "valuationKrw=null"),
        Set.of(),
        20,
        AgentTokenUsage.unknown());
  }
}
