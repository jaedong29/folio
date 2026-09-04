package com.assetdashboard.evidence.trace;

/** 원문을 저장하지 않고 모델 사용량만 남기는 값 객체. */
public record AgentTokenUsage(Long inputTokens, Long outputTokens) {

  public AgentTokenUsage {
    if (inputTokens != null && inputTokens < 0) {
      throw new IllegalArgumentException("inputTokens는 음수일 수 없습니다.");
    }
    if (outputTokens != null && outputTokens < 0) {
      throw new IllegalArgumentException("outputTokens는 음수일 수 없습니다.");
    }
  }

  public static AgentTokenUsage unknown() {
    return new AgentTokenUsage(null, null);
  }
}
