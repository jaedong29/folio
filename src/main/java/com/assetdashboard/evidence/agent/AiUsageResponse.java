package com.assetdashboard.evidence.agent;

import java.time.LocalDate;

/** 오늘 누적된 NIM 호출·토큰 사용량과 설정된 예산을 함께 보여준다. */
public record AiUsageResponse(
    LocalDate date,
    long callCount,
    long inputTokens,
    long outputTokens,
    int dailyCallLimit,
    long dailyTokenLimit) {

  static AiUsageResponse of(LlmDailyUsage usage, FinancialAgentProperties properties) {
    return new AiUsageResponse(
        usage.getUsageDate(),
        usage.getCallCount(),
        usage.getInputTokens(),
        usage.getOutputTokens(),
        properties.dailyCallLimit(),
        properties.dailyTokenLimit());
  }
}
