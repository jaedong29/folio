package com.assetdashboard.evidence.trace;

import java.util.List;
import java.util.Objects;

/** 한 Agent 실행에서 관찰된 모델·Tool·검색·Guardrail 단계. */
public record AgentTraceStep(
    String spanId,
    String parentSpanId,
    AgentTraceStepType type,
    String name,
    AgentTraceStepStatus status,
    long latencyMs,
    String errorCode,
    List<String> referenceIds) {

  public AgentTraceStep {
    if (spanId == null || spanId.isBlank()) {
      throw new IllegalArgumentException("spanId가 필요합니다.");
    }
    Objects.requireNonNull(type, "type이 필요합니다.");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("단계 이름이 필요합니다.");
    }
    Objects.requireNonNull(status, "status가 필요합니다.");
    if (latencyMs < 0) {
      throw new IllegalArgumentException("latencyMs는 음수일 수 없습니다.");
    }
    referenceIds = referenceIds == null ? List.of() : List.copyOf(referenceIds);
  }
}
