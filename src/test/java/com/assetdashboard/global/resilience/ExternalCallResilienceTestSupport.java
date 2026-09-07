package com.assetdashboard.global.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;

/** 외부 HTTP adapter 단위 테스트에서 실제 회복성 장식을 통과시키는 최소 factory. */
public final class ExternalCallResilienceTestSupport {

  private ExternalCallResilienceTestSupport() {}

  public static ExternalCallResilience create() {
    return create(10, 5, 3);
  }

  public static ExternalCallResilience create(
      int slidingWindowSize, int minimumNumberOfCalls, int readMaxAttempts) {
    ExternalCallResilience resilience =
        new ExternalCallResilience(
            new ExternalResilienceProperties(
                slidingWindowSize,
                minimumNumberOfCalls,
                50,
                2,
                30_000,
                readMaxAttempts,
                readMaxAttempts,
                readMaxAttempts,
                readMaxAttempts,
                0),
            CircuitBreakerRegistry.ofDefaults(),
            RetryRegistry.ofDefaults());
    resilience.initialize();
    return resilience;
  }
}
