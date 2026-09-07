package com.assetdashboard.evidence.evaluation;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.trace.AgentTraceResponse;
import com.assetdashboard.evidence.trace.AgentTraceStepType;
import com.assetdashboard.global.common.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "live_evaluation_batch_cases",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_live_eval_batch_case",
            columnNames = {"batch_job_id", "case_id"}),
    indexes = @Index(name = "idx_live_eval_case_trace", columnList = "trace_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveEvaluationBatchCaseResult extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "batch_job_id", nullable = false)
  private Long batchJobId;

  @Column(name = "case_id", nullable = false, length = 80)
  private String caseId;

  @Column(name = "trace_id", length = 36)
  private String traceId;

  @Column(nullable = false)
  private boolean passed;

  @Column(name = "hard_failure", nullable = false)
  private boolean hardFailure;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private EvidenceConclusion conclusion;

  @Column(name = "latency_ms", nullable = false)
  private long latencyMs;

  @Column(name = "input_tokens", nullable = false)
  private long inputTokens;

  @Column(name = "output_tokens", nullable = false)
  private long outputTokens;

  @Column(name = "model_call_count", nullable = false)
  private int modelCallCount;

  @Column(name = "failure_codes", length = 500)
  private String failureCodes;

  @Column(name = "error_code", length = 80)
  private String errorCode;

  public static LiveEvaluationBatchCaseResult completed(
      Long batchJobId, String caseId, AgentTraceResponse trace) {
    LiveEvaluationBatchCaseResult result = new LiveEvaluationBatchCaseResult();
    result.batchJobId = batchJobId;
    result.caseId = caseId;
    result.traceId = trace.traceId();
    result.passed = trace.evaluation() != null && trace.evaluation().passed();
    result.hardFailure = trace.hardFailure();
    result.conclusion = trace.conclusion();
    result.latencyMs = trace.latencyMs();
    result.inputTokens = trace.inputTokens() == null ? 0 : trace.inputTokens();
    result.outputTokens = trace.outputTokens() == null ? 0 : trace.outputTokens();
    result.modelCallCount = countModelCalls(trace.steps());
    result.failureCodes =
        trace.evaluation() == null ? "" : String.join(",", trace.evaluation().failureCodes());
    return result;
  }

  private static int countModelCalls(List<AgentTraceResponse.AgentTraceNode> nodes) {
    if (nodes == null) {
      return 0;
    }
    return nodes.stream()
        .mapToInt(
            node ->
                (node.type() == AgentTraceStepType.MODEL ? 1 : 0)
                    + countModelCalls(node.children()))
        .sum();
  }

  public static LiveEvaluationBatchCaseResult failed(
      Long batchJobId, String caseId, String safeErrorCode, long latencyMs) {
    LiveEvaluationBatchCaseResult result = new LiveEvaluationBatchCaseResult();
    result.batchJobId = batchJobId;
    result.caseId = caseId;
    result.passed = false;
    result.hardFailure = true;
    result.latencyMs = latencyMs;
    result.failureCodes = "EXECUTION_ERROR";
    result.errorCode = safeErrorCode;
    return result;
  }

  public boolean isPassed() {
    return passed;
  }

  public boolean isHardFailure() {
    return hardFailure;
  }
}
