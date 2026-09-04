package com.assetdashboard.evidence.trace;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 민감한 질문·답변 원문을 제외하고 저장하는 Agent 실행 요약. */
@Getter
@Entity
@Table(
    name = "ai_agent_runs",
    uniqueConstraints = @UniqueConstraint(name = "uk_ai_run_trace", columnNames = "trace_id"),
    indexes = @Index(name = "idx_ai_run_user_created", columnList = "user_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentTraceRun extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "trace_id", nullable = false, length = 36)
  private String traceId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "case_id", length = 100)
  private String caseId;

  @Column(name = "question_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
  private String questionHash;

  @Column(name = "answer_hash", length = 64, columnDefinition = "CHAR(64)")
  private String answerHash;

  @Column(name = "prompt_version", nullable = false, length = 80)
  private String promptVersion;

  @Column(nullable = false, length = 120)
  private String model;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AgentRunStatus status;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private EvidenceConclusion conclusion;

  @Column(name = "latency_ms", nullable = false)
  private long latencyMs;

  @Column(name = "input_tokens")
  private Long inputTokens;

  @Column(name = "output_tokens")
  private Long outputTokens;

  @Column(name = "hard_failure", nullable = false)
  private boolean hardFailure;

  private AgentTraceRun(
      String traceId,
      Long userId,
      String caseId,
      String questionHash,
      String answerHash,
      String promptVersion,
      String model,
      AgentRunStatus status,
      EvidenceConclusion conclusion,
      long latencyMs,
      Long inputTokens,
      Long outputTokens,
      boolean hardFailure) {
    this.traceId = traceId;
    this.userId = userId;
    this.caseId = caseId;
    this.questionHash = questionHash;
    this.answerHash = answerHash;
    this.promptVersion = promptVersion;
    this.model = model;
    this.status = status;
    this.conclusion = conclusion;
    this.latencyMs = latencyMs;
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
    this.hardFailure = hardFailure;
  }

  public static AgentTraceRun create(
      Long userId,
      String caseId,
      String questionHash,
      String answerHash,
      AgentRunResult result,
      boolean hardFailure) {
    return new AgentTraceRun(
        result.traceId(),
        userId,
        caseId,
        questionHash,
        answerHash,
        result.promptVersion(),
        result.model(),
        result.status(),
        result.conclusion(),
        result.latencyMs(),
        result.tokenUsage().inputTokens(),
        result.tokenUsage().outputTokens(),
        hardFailure);
  }
}
