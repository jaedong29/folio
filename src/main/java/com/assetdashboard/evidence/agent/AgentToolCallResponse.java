package com.assetdashboard.evidence.agent;

import com.assetdashboard.evidence.trace.AgentTokenUsage;

/** 첫 모델 호출에서 선택된 Tool과 구조화된 인자. */
public record AgentToolCallResponse(
    String callId,
    String toolName,
    Long assetId,
    String model,
    long latencyMs,
    AgentTokenUsage tokenUsage) {

  public AgentToolCallResponse {
    if (callId == null || callId.isBlank()) {
      throw new IllegalArgumentException("Tool call id가 필요합니다.");
    }
    if (toolName == null || toolName.isBlank()) {
      throw new IllegalArgumentException("Tool 이름이 필요합니다.");
    }
    if (assetId == null || assetId < 1) {
      throw new IllegalArgumentException("유효한 assetId가 필요합니다.");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("모델 이름이 필요합니다.");
    }
    if (latencyMs < 0) {
      throw new IllegalArgumentException("latencyMs는 음수일 수 없습니다.");
    }
    tokenUsage = tokenUsage == null ? AgentTokenUsage.unknown() : tokenUsage;
  }
}
