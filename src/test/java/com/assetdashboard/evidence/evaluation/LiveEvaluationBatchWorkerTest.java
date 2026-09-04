package com.assetdashboard.evidence.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetdashboard.evidence.agent.FinancialEvidenceAgentService;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.trace.AgentRunStatus;
import com.assetdashboard.evidence.trace.AgentTraceResponse;
import com.assetdashboard.evidence.trace.AgentTraceStepStatus;
import com.assetdashboard.evidence.trace.AgentTraceStepType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LiveEvaluationBatchWorkerTest {

  private final LiveEvaluationBatchLifecycleService lifecycleService =
      mock(LiveEvaluationBatchLifecycleService.class);
  private final FinancialAgentEvaluationFixtureService fixtureService =
      mock(FinancialAgentEvaluationFixtureService.class);
  private final FinancialEvidenceAgentService agentService =
      mock(FinancialEvidenceAgentService.class);
  private final LiveEvaluationBatchWorker worker =
      new LiveEvaluationBatchWorker(lifecycleService, fixtureService, agentService);

  @Test
  void runsClaimedCasesAndStoresOnlyTraceMetrics() {
    ClaimedLiveEvaluationBatch batch =
        new ClaimedLiveEvaluationBatch(11L, "batch-id", 7L, List.of("missing-fx"));
    when(lifecycleService.claimNext()).thenReturn(Optional.of(batch));
    when(fixtureService.create(7L, "missing-fx"))
        .thenReturn(new EvaluationFixtureResponse(42L, "missing-fx", "fixture"));
    when(agentService.evaluate(7L, 42L, "missing-fx")).thenReturn(trace());

    worker.processNext();

    ArgumentCaptor<LiveEvaluationBatchCaseResult> captor =
        ArgumentCaptor.forClass(LiveEvaluationBatchCaseResult.class);
    verify(lifecycleService).record(captor.capture());
    verify(lifecycleService).complete(11L);
    assertThat(captor.getValue().getTraceId()).isEqualTo("trace-id");
    assertThat(captor.getValue().getLatencyMs()).isEqualTo(500);
    assertThat(captor.getValue().getInputTokens()).isEqualTo(120);
    assertThat(captor.getValue().getOutputTokens()).isEqualTo(30);
    assertThat(captor.getValue().getModelCallCount()).isEqualTo(2);
  }

  private AgentTraceResponse trace() {
    return new AgentTraceResponse(
        "trace-id",
        "missing-fx",
        "financial-agent-v3",
        "model",
        AgentRunStatus.COMPLETED,
        EvidenceConclusion.UNAVAILABLE,
        500,
        120L,
        30L,
        false,
        false,
        false,
        LocalDateTime.parse("2026-09-05T00:00:00"),
        traceSteps(),
        new AgentTraceResponse.EvaluationSummary(
            "missing-fx", "rule-v1", true, false, List.of(), List.of()));
  }

  private List<AgentTraceResponse.AgentTraceNode> traceSteps() {
    AgentTraceResponse.AgentTraceNode answer =
        new AgentTraceResponse.AgentTraceNode(
            "model-answer",
            AgentTraceStepType.MODEL,
            "answer",
            AgentTraceStepStatus.SUCCESS,
            300,
            null,
            List.of(),
            List.of());
    AgentTraceResponse.AgentTraceNode plan =
        new AgentTraceResponse.AgentTraceNode(
            "model-plan",
            AgentTraceStepType.MODEL,
            "plan",
            AgentTraceStepStatus.SUCCESS,
            200,
            null,
            List.of(),
            List.of(answer));
    return List.of(
        new AgentTraceResponse.AgentTraceNode(
            "root",
            AgentTraceStepType.AGENT,
            "agent",
            AgentTraceStepStatus.SUCCESS,
            500,
            null,
            List.of(),
            List.of(plan)));
  }
}
