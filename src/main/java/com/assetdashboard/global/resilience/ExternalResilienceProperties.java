package com.assetdashboard.global.resilience;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 외부 출처별 Circuit Breaker와 읽기 요청 재시도 정책. */
@ConfigurationProperties(prefix = "app.external-resilience")
public record ExternalResilienceProperties(
    int slidingWindowSize,
    int minimumNumberOfCalls,
    float failureRateThreshold,
    int permittedCallsInHalfOpenState,
    long openStateWaitMillis,
    int yahooReadMaxAttempts,
    int binanceReadMaxAttempts,
    int upbitReadMaxAttempts,
    int githubReadMaxAttempts,
    long readRetryInitialWaitMillis) {

  public ExternalResilienceProperties {
    if (slidingWindowSize < 1) {
      throw new IllegalArgumentException("slidingWindowSize는 1 이상이어야 합니다.");
    }
    if (minimumNumberOfCalls < 1 || minimumNumberOfCalls > slidingWindowSize) {
      throw new IllegalArgumentException("minimumNumberOfCalls는 1 이상 slidingWindowSize 이하여야 합니다.");
    }
    if (failureRateThreshold <= 0 || failureRateThreshold > 100) {
      throw new IllegalArgumentException("failureRateThreshold는 0 초과 100 이하여야 합니다.");
    }
    if (permittedCallsInHalfOpenState < 1 || openStateWaitMillis < 1) {
      throw new IllegalArgumentException("Half-open 호출 수와 open 대기시간은 1 이상이어야 합니다.");
    }
    if (yahooReadMaxAttempts < 1
        || binanceReadMaxAttempts < 1
        || upbitReadMaxAttempts < 1
        || githubReadMaxAttempts < 1
        || yahooReadMaxAttempts > 10
        || binanceReadMaxAttempts > 10
        || upbitReadMaxAttempts > 10
        || githubReadMaxAttempts > 10
        || readRetryInitialWaitMillis < 0) {
      throw new IllegalArgumentException("출처별 읽기 시도 횟수는 1~10이고 대기시간은 음수일 수 없습니다.");
    }
  }

  public Duration openStateWait() {
    return Duration.ofMillis(openStateWaitMillis);
  }

  public long readRetryWaitMillis(int retryAttempt) {
    int boundedExponent = Math.max(0, Math.min(retryAttempt - 1, 30));
    long multiplier = 1L << boundedExponent;
    try {
      return Math.multiplyExact(readRetryInitialWaitMillis, multiplier);
    } catch (ArithmeticException ignored) {
      return Long.MAX_VALUE;
    }
  }

  public int readMaxAttempts(ExternalSource source) {
    return switch (source) {
      case YAHOO_FINANCE -> yahooReadMaxAttempts;
      case BINANCE -> binanceReadMaxAttempts;
      case UPBIT -> upbitReadMaxAttempts;
      case GITHUB_RELEASES -> githubReadMaxAttempts;
      case NVIDIA_NIM -> 1;
    };
  }
}
