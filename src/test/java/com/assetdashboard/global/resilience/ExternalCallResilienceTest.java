package com.assetdashboard.global.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class ExternalCallResilienceTest {

  @Test
  void doublesRetryWaitFromTheConfiguredInitialDelay() {
    ExternalResilienceProperties properties =
        new ExternalResilienceProperties(10, 5, 50, 2, 30_000, 2, 2, 2, 3, 200);

    assertThat(properties.readRetryWaitMillis(1)).isEqualTo(200);
    assertThat(properties.readRetryWaitMillis(2)).isEqualTo(400);
    assertThat(properties.readRetryWaitMillis(3)).isEqualTo(800);
  }

  @Test
  void retriesTransientReadFailureAndReturnsTheLaterSuccess() {
    ExternalCallResilience resilience = ExternalCallResilienceTestSupport.create();
    AtomicInteger attempts = new AtomicInteger();

    String result =
        resilience.executeRead(
            ExternalSource.BINANCE,
            () -> {
              if (attempts.incrementAndGet() < 3) {
                throw new ResourceAccessException("timeout");
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts).hasValue(3);
    assertThat(resilience.circuitBreaker(ExternalSource.BINANCE).getMetrics().getNumberOfSuccessfulCalls())
        .isEqualTo(1);
  }

  @Test
  void countsOneFailedLogicalReadAfterAllRetriesAndThenOpens() {
    ExternalCallResilience resilience = ExternalCallResilienceTestSupport.create(2, 2, 3);
    AtomicInteger attempts = new AtomicInteger();

    for (int request = 0; request < 2; request++) {
      assertThatThrownBy(
              () ->
                  resilience.executeRead(
                      ExternalSource.GITHUB_RELEASES,
                      () -> {
                        attempts.incrementAndGet();
                        throw new ResourceAccessException("timeout");
                      }))
          .isInstanceOf(ResourceAccessException.class);
    }

    assertThat(attempts).hasValue(6);
    assertThat(resilience.circuitBreaker(ExternalSource.GITHUB_RELEASES).getState())
        .isEqualTo(State.OPEN);
    assertThat(
            resilience
                .circuitBreaker(ExternalSource.GITHUB_RELEASES)
                .getMetrics()
                .getNumberOfFailedCalls())
        .isEqualTo(2);
    assertThatThrownBy(
            () ->
                resilience.executeRead(
                    ExternalSource.GITHUB_RELEASES,
                    () -> {
                      attempts.incrementAndGet();
                      return "not-called";
                    }))
        .isInstanceOf(ExternalCallRejectedException.class);
    assertThat(attempts).hasValue(6);
  }

  @Test
  void ignoresNonRateLimitClientErrorWithoutRetryingOrPollutingBreakerMetrics() {
    ExternalCallResilience resilience = ExternalCallResilienceTestSupport.create();
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                resilience.executeRead(
                    ExternalSource.YAHOO_FINANCE,
                    () -> {
                      attempts.incrementAndGet();
                      throw HttpClientErrorException.create(
                          HttpStatus.NOT_FOUND, "not found", null, null, null);
                    }))
        .isInstanceOf(HttpClientErrorException.NotFound.class);

    assertThat(attempts).hasValue(1);
    assertThat(
            resilience
                .circuitBreaker(ExternalSource.YAHOO_FINANCE)
                .getMetrics()
                .getNumberOfBufferedCalls())
        .isZero();
  }

  @Test
  void isolatesBreakerStateBySource() {
    ExternalCallResilience resilience = ExternalCallResilienceTestSupport.create(2, 2, 1);
    for (int request = 0; request < 2; request++) {
      assertThatThrownBy(
              () ->
                  resilience.executeRead(
                      ExternalSource.GITHUB_RELEASES,
                      () -> {
                        throw new ResourceAccessException("timeout");
                      }))
          .isInstanceOf(ResourceAccessException.class);
    }

    assertThat(resilience.executeRead(ExternalSource.YAHOO_FINANCE, () -> "ok")).isEqualTo("ok");
    assertThat(resilience.circuitBreaker(ExternalSource.GITHUB_RELEASES).getState())
        .isEqualTo(State.OPEN);
    assertThat(resilience.circuitBreaker(ExternalSource.YAHOO_FINANCE).getState())
        .isEqualTo(State.CLOSED);
  }

  @Test
  void neverRetriesPotentiallyChargeablePost() {
    ExternalCallResilience resilience = ExternalCallResilienceTestSupport.create();
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                resilience.executeOnce(
                    ExternalSource.NVIDIA_NIM,
                    () -> {
                      attempts.incrementAndGet();
                      throw new ResourceAccessException("response lost");
                    }))
        .isInstanceOf(ResourceAccessException.class);

    assertThat(attempts).hasValue(1);
    assertThat(
            resilience
                .circuitBreaker(ExternalSource.NVIDIA_NIM)
                .getMetrics()
                .getNumberOfFailedCalls())
        .isEqualTo(1);
  }

  @Test
  void rejectsUsingTheReadRetryPathForNim() {
    ExternalCallResilience resilience = ExternalCallResilienceTestSupport.create();
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                resilience.executeRead(
                    ExternalSource.NVIDIA_NIM,
                    () -> {
                      attempts.incrementAndGet();
                      return "not-called";
                    }))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(attempts).hasValue(0);
  }
}
