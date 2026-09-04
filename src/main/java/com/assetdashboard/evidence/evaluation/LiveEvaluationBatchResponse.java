package com.assetdashboard.evidence.evaluation;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record LiveEvaluationBatchResponse(
    String batchId,
    String mode,
    LiveEvaluationBatchStatus status,
    List<String> caseIds,
    int requestedCount,
    int completedCount,
    int passedCount,
    int hardFailureCount,
    long totalLatencyMs,
    long totalInputTokens,
    long totalOutputTokens,
    int observedModelCalls,
    int maximumProviderCalls,
    Long averageLatencyMs,
    Long p95LatencyMs,
    Instant startedAt,
    Instant completedAt,
    String errorCode,
    boolean reused,
    boolean rawQuestionStored,
    boolean rawAnswerStored,
    List<CaseResult> results) {

  static LiveEvaluationBatchResponse from(
      LiveEvaluationBatchJob job, List<LiveEvaluationBatchCaseResult> results, boolean reused) {
    List<Long> latencies =
        results.stream().map(LiveEvaluationBatchCaseResult::getLatencyMs).sorted().toList();
    Long average =
        latencies.isEmpty()
            ? null
            : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0));
    Long p95 =
        latencies.isEmpty()
            ? null
            : latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);
    return new LiveEvaluationBatchResponse(
        job.getBatchId(),
        "LIVE_NIM",
        job.getStatus(),
        job.requestedCaseIds(),
        job.requestedCaseIds().size(),
        job.getCompletedCount(),
        job.getPassedCount(),
        job.getHardFailureCount(),
        job.getTotalLatencyMs(),
        job.getTotalInputTokens(),
        job.getTotalOutputTokens(),
        job.getObservedModelCalls(),
        job.requestedCaseIds().size() * 2,
        average,
        p95,
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getErrorCode(),
        reused,
        false,
        false,
        results.stream().map(CaseResult::from).toList());
  }

  public record CaseResult(
      String caseId,
      String traceId,
      boolean passed,
      boolean hardFailure,
      EvidenceConclusion conclusion,
      long latencyMs,
      long inputTokens,
      long outputTokens,
      int modelCallCount,
      List<String> failureCodes,
      String errorCode) {

    static CaseResult from(LiveEvaluationBatchCaseResult result) {
      List<String> codes =
          result.getFailureCodes() == null || result.getFailureCodes().isBlank()
              ? List.of()
              : Arrays.asList(result.getFailureCodes().split(","));
      return new CaseResult(
          result.getCaseId(),
          result.getTraceId(),
          result.isPassed(),
          result.isHardFailure(),
          result.getConclusion(),
          result.getLatencyMs(),
          result.getInputTokens(),
          result.getOutputTokens(),
          result.getModelCallCount(),
          codes,
          result.getErrorCode());
    }
  }
}
