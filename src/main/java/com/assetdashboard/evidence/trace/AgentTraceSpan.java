package com.assetdashboard.evidence.trace;

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

/** parentSpanId로 Agent 실행 단계를 트리 형태로 연결한 안전한 관찰 기록. */
@Getter
@Entity
@Table(
    name = "ai_agent_spans",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_ai_span_run_span", columnNames = {"run_id", "span_id"}),
    indexes = @Index(name = "idx_ai_span_run_parent", columnList = "run_id, parent_span_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentTraceSpan extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "run_id", nullable = false)
  private Long runId;

  @Column(name = "span_id", nullable = false, length = 64)
  private String spanId;

  @Column(name = "parent_span_id", length = 64)
  private String parentSpanId;

  @Enumerated(EnumType.STRING)
  @Column(name = "step_type", nullable = false, length = 20)
  private AgentTraceStepType type;

  @Column(nullable = false, length = 120)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AgentTraceStepStatus status;

  @Column(name = "latency_ms", nullable = false)
  private long latencyMs;

  @Column(name = "error_code", length = 80)
  private String errorCode;

  /** 문서 id·내부 trace id처럼 원문이 아닌 불투명 참조값만 저장한다. */
  @Column(name = "reference_ids", length = 2000)
  private String referenceIds;

  private AgentTraceSpan(
      Long runId,
      String spanId,
      String parentSpanId,
      AgentTraceStepType type,
      String name,
      AgentTraceStepStatus status,
      long latencyMs,
      String errorCode,
      String referenceIds) {
    this.runId = runId;
    this.spanId = spanId;
    this.parentSpanId = parentSpanId;
    this.type = type;
    this.name = name;
    this.status = status;
    this.latencyMs = latencyMs;
    this.errorCode = errorCode;
    this.referenceIds = referenceIds;
  }

  public static AgentTraceSpan create(Long runId, AgentTraceStep step) {
    return new AgentTraceSpan(
        runId,
        step.spanId(),
        step.parentSpanId(),
        step.type(),
        step.name(),
        step.status(),
        step.latencyMs(),
        step.errorCode(),
        step.referenceIds().isEmpty() ? null : String.join(",", step.referenceIds()));
  }
}
