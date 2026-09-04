package com.assetdashboard.evidence.evaluation;

import com.assetdashboard.evidence.agent.FinancialEvidenceAgentService;
import com.assetdashboard.evidence.trace.AgentTraceResponse;
import com.assetdashboard.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("local")
@Component
@RequiredArgsConstructor
public class LiveEvaluationBatchWorker {

  private final LiveEvaluationBatchLifecycleService lifecycleService;
  private final FinancialAgentEvaluationFixtureService fixtureService;
  private final FinancialEvidenceAgentService agentService;

  @EventListener(ApplicationReadyEvent.class)
  public void recoverInterruptedJobs() {
    lifecycleService.recoverInterrupted();
  }

  @Scheduled(fixedDelayString = "${app.ai.evaluation-worker-delay-millis:1500}")
  public void processNext() {
    Optional<ClaimedLiveEvaluationBatch> candidate = lifecycleService.claimNext();
    if (candidate.isEmpty()) return;
    ClaimedLiveEvaluationBatch batch = candidate.get();
    try {
      for (String caseId : batch.caseIds()) {
        if (lifecycleService.hasResult(batch.id(), caseId)) continue;
        long startedAt = System.nanoTime();
        try {
          EvaluationFixtureResponse fixture = fixtureService.create(batch.userId(), caseId);
          AgentTraceResponse trace =
              agentService.evaluate(batch.userId(), fixture.assetId(), caseId);
          lifecycleService.record(
              LiveEvaluationBatchCaseResult.completed(batch.id(), caseId, trace));
        } catch (BusinessException exception) {
          lifecycleService.record(
              LiveEvaluationBatchCaseResult.failed(
                  batch.id(), caseId, exception.getErrorCode().name(), elapsedMs(startedAt)));
        } catch (RuntimeException exception) {
          lifecycleService.record(
              LiveEvaluationBatchCaseResult.failed(
                  batch.id(), caseId, "UNEXPECTED_EVALUATION_FAILURE", elapsedMs(startedAt)));
        }
      }
      lifecycleService.complete(batch.id());
    } catch (RuntimeException exception) {
      lifecycleService.fail(batch.id(), "UNEXPECTED_BATCH_FAILURE");
      log.error("[LiveEvaluation] batch failed batchId={} type={}", batch.batchId(), exception.getClass().getSimpleName());
    }
  }

  private long elapsedMs(long startedAt) {
    return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
  }
}
