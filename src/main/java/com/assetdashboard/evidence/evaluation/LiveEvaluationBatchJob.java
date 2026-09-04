package com.assetdashboard.evidence.evaluation;

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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "live_evaluation_batches",
    uniqueConstraints = @UniqueConstraint(name = "uk_live_eval_batch", columnNames = "batch_id"),
    indexes = {
      @Index(name = "idx_live_eval_user_created", columnList = "user_id, created_at"),
      @Index(name = "idx_live_eval_status_created", columnList = "status, created_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveEvaluationBatchJob extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "batch_id", nullable = false, length = 36)
  private String batchId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "case_ids", nullable = false, length = 500)
  private String caseIds;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LiveEvaluationBatchStatus status;

  @Column(name = "started_at") private Instant startedAt;
  @Column(name = "completed_at") private Instant completedAt;
  @Column(name = "completed_count", nullable = false) private int completedCount;
  @Column(name = "passed_count", nullable = false) private int passedCount;
  @Column(name = "hard_failure_count", nullable = false) private int hardFailureCount;
  @Column(name = "total_latency_ms", nullable = false) private long totalLatencyMs;
  @Column(name = "total_input_tokens", nullable = false) private long totalInputTokens;
  @Column(name = "total_output_tokens", nullable = false) private long totalOutputTokens;
  @Column(name = "observed_model_calls", nullable = false) private int observedModelCalls;
  @Column(name = "error_code", length = 80) private String errorCode;

  @Version @Column(nullable = false) private Long version;

  private LiveEvaluationBatchJob(Long userId, List<String> caseIds) {
    this.batchId = UUID.randomUUID().toString();
    this.userId = userId;
    this.caseIds = String.join(",", caseIds);
    this.status = LiveEvaluationBatchStatus.PENDING;
  }

  public static LiveEvaluationBatchJob pending(Long userId, List<String> caseIds) {
    return new LiveEvaluationBatchJob(userId, caseIds);
  }

  public List<String> requestedCaseIds() {
    return caseIds.isBlank() ? List.of() : Arrays.asList(caseIds.split(","));
  }

  public void start(Instant now) {
    status = LiveEvaluationBatchStatus.RUNNING;
    startedAt = now;
    errorCode = null;
  }

  public void addResult(LiveEvaluationBatchCaseResult result) {
    if (status != LiveEvaluationBatchStatus.RUNNING) {
      throw new IllegalStateException("RUNNING 평가 배치에만 결과를 추가할 수 있습니다.");
    }
    completedCount++;
    if (result.isPassed()) passedCount++;
    if (result.isHardFailure()) hardFailureCount++;
    totalLatencyMs += result.getLatencyMs();
    totalInputTokens += result.getInputTokens();
    totalOutputTokens += result.getOutputTokens();
    observedModelCalls += result.getModelCallCount();
  }

  public void complete(Instant now) {
    if (completedCount != requestedCaseIds().size()) {
      throw new IllegalStateException("모든 평가 케이스가 기록되기 전에 완료할 수 없습니다.");
    }
    status = LiveEvaluationBatchStatus.COMPLETED;
    completedAt = now;
  }

  public void fail(Instant now, String safeErrorCode) {
    status = LiveEvaluationBatchStatus.FAILED;
    completedAt = now;
    errorCode = safeErrorCode;
  }

  public void retryInterrupted() {
    status = LiveEvaluationBatchStatus.PENDING;
    startedAt = null;
    completedAt = null;
    errorCode = null;
  }
}
