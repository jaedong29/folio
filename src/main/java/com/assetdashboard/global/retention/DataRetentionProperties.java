package com.assetdashboard.global.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 개인정보·운영 기록별 보존 기간. 각 값은 운영 환경변수로 조정할 수 있다. */
@ConfigurationProperties(prefix = "app.retention")
public record DataRetentionProperties(
    int auditLogDays,
    int agentTraceDays,
    int evaluationBatchDays,
    long cleanupDelayMillis) {

  public DataRetentionProperties {
    if (auditLogDays < 1 || agentTraceDays < 1 || evaluationBatchDays < 1) {
      throw new IllegalArgumentException("보존 기간은 1일 이상이어야 합니다.");
    }
    if (cleanupDelayMillis < 1) {
      throw new IllegalArgumentException("보존 기록 정리 주기는 1ms 이상이어야 합니다.");
    }
  }
}
