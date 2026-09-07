package com.assetdashboard.global.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.ResourceAccessException;

/** 실제 Spring 설정 binding·Resilience4j registry·Micrometer 연결을 함께 검증한다. */
@SpringBootTest(
    properties = {
      "app.external-resilience.sliding-window-size=2",
      "app.external-resilience.minimum-number-of-calls=2",
      "app.external-resilience.failure-rate-threshold=50",
      "app.external-resilience.github-read-max-attempts=2",
      "app.external-resilience.read-retry-initial-wait-millis=0"
    })
class ExternalCallResilienceIntegrationTest {

  @Autowired private ExternalCallResilience resilience;
  @Autowired private ExternalResilienceProperties properties;
  @Autowired private MeterRegistry meterRegistry;

  @Test
  void bindsConfigurationRegistersMetricsAndKeepsSourcesIndependent() {
    assertThat(properties.readMaxAttempts(ExternalSource.GITHUB_RELEASES)).isEqualTo(2);
    assertThat(properties.readMaxAttempts(ExternalSource.NVIDIA_NIM)).isEqualTo(1);
    AtomicInteger githubAttempts = new AtomicInteger();
    for (int request = 0; request < 2; request++) {
      assertThatThrownBy(
              () ->
                  resilience.executeRead(
                      ExternalSource.GITHUB_RELEASES,
                      () -> {
                        githubAttempts.incrementAndGet();
                        throw new ResourceAccessException("timeout");
                      }))
          .isInstanceOf(ResourceAccessException.class);
    }

    assertThat(githubAttempts).hasValue(4);
    assertThat(resilience.circuitBreaker(ExternalSource.GITHUB_RELEASES).getState())
        .isEqualTo(State.OPEN);
    assertThat(resilience.executeRead(ExternalSource.BINANCE, () -> "ok")).isEqualTo("ok");
    assertThat(resilience.circuitBreaker(ExternalSource.BINANCE).getState())
        .isEqualTo(State.CLOSED);
    assertThat(
            meterRegistry
                .find("resilience4j.circuitbreaker.calls")
                .tag("name", "github_releases")
                .meters())
        .isNotEmpty();
    assertThat(
            meterRegistry.find("resilience4j.retry.calls").tag("name", "github_releases").meters())
        .isNotEmpty();
  }
}
