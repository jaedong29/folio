package com.assetdashboard.evidence.agent;

import com.assetdashboard.evidence.trace.AgentTokenUsage;

/**
 * 모델 제공자가 반환할 수 있는 값의 경계.
 *
 * <p>결론과 evidenceFacts는 포함하지 않는다. 모델은 근거를 설명하는 최종 문장만 생성한다.
 */
public record AgentModelResponse(
    String finalAnswer, String model, long latencyMs, AgentTokenUsage tokenUsage) {

  public AgentModelResponse {
    if (finalAnswer == null || finalAnswer.isBlank()) {
      throw new IllegalArgumentException("모델 최종 답변이 필요합니다.");
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
