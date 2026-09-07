package com.assetdashboard.global.resilience;

import com.assetdashboard.infra.price.PriceProviderException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 외부 출처별 장애 상태를 격리하고, 멱등인 읽기 요청에만 제한된 재시도를 적용한다.
 *
 * <p>Circuit Breaker를 Retry 바깥에 두므로 한 논리 요청이 내부에서 세 번 시도돼도 차단기에는 최종 결과 한 건으로
 * 기록된다. NIM POST는 응답 유실 시 같은 요청이 중복 과금될 수 있어 {@link #executeOnce}로 재시도하지 않는다.
 */
@Slf4j
@Component
public class ExternalCallResilience {

  private final ExternalResilienceProperties properties;
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final RetryRegistry retryRegistry;
  private final Map<ExternalSource, CircuitBreaker> circuitBreakers =
      new EnumMap<>(ExternalSource.class);
  private final Map<ExternalSource, Retry> retries = new EnumMap<>(ExternalSource.class);

  public ExternalCallResilience(
      ExternalResilienceProperties properties,
      CircuitBreakerRegistry circuitBreakerRegistry,
      RetryRegistry retryRegistry) {
    this.properties = properties;
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.retryRegistry = retryRegistry;
  }

  @PostConstruct
  void initialize() {
    CircuitBreakerConfig breakerConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(properties.slidingWindowSize())
            .minimumNumberOfCalls(properties.minimumNumberOfCalls())
            .failureRateThreshold(properties.failureRateThreshold())
            .permittedNumberOfCallsInHalfOpenState(properties.permittedCallsInHalfOpenState())
            .waitDurationInOpenState(properties.openStateWait())
            .ignoreException(this::ignoreForBreaker)
            .recordException(this::recordAsFailure)
            .build();
    for (ExternalSource source : ExternalSource.values()) {
      String name = source.name().toLowerCase();
      CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(name, breakerConfig);
      breaker
          .getEventPublisher()
          .onStateTransition(
              event ->
                  log.warn(
                      "외부 호출 Circuit Breaker 상태 변경 source={} transition={}",
                      source,
                      event.getStateTransition()));
      circuitBreakers.put(source, breaker);
      if (source != ExternalSource.NVIDIA_NIM) {
        RetryConfig retryConfig =
            RetryConfig.custom()
                .maxAttempts(properties.readMaxAttempts(source))
                .intervalFunction(properties::readRetryWaitMillis)
                .retryOnException(this::isRetryableReadFailure)
                .build();
        Retry retry = retryRegistry.retry(name, retryConfig);
        retry
            .getEventPublisher()
            .onRetry(
                event ->
                    log.warn(
                        "외부 읽기 호출 재시도 source={} attempt={} cause={}",
                        source,
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getClass().getSimpleName()));
        retries.put(source, retry);
      }
    }
  }

  /** 네트워크 오류와 5xx만 재시도할 수 있는 GET 계열 호출. */
  public <T> T executeRead(ExternalSource source, Supplier<T> supplier) {
    Retry retry = retries.get(source);
    if (retry == null) {
      throw new IllegalArgumentException("재시도할 수 없는 외부 출처입니다: " + source);
    }
    Supplier<T> retried = Retry.decorateSupplier(retry, supplier);
    return executeWithBreaker(source, retried);
  }

  /** 재전송의 부작용을 배제할 수 없는 POST 계열 호출. */
  public <T> T executeOnce(ExternalSource source, Supplier<T> supplier) {
    return executeWithBreaker(source, supplier);
  }

  CircuitBreaker circuitBreaker(ExternalSource source) {
    return circuitBreakers.get(source);
  }

  Retry retry(ExternalSource source) {
    return retries.get(source);
  }

  private <T> T executeWithBreaker(ExternalSource source, Supplier<T> supplier) {
    try {
      return CircuitBreaker.decorateSupplier(circuitBreakers.get(source), supplier).get();
    } catch (CallNotPermittedException e) {
      log.warn("외부 호출 Circuit Breaker 차단 source={}", source);
      throw new ExternalCallRejectedException(source, e);
    }
  }

  private boolean isRetryableReadFailure(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof ResourceAccessException) {
        return true;
      }
      if (current instanceof RestClientResponseException responseException) {
        return responseException.getStatusCode().is5xxServerError();
      }
      current = current.getCause();
    }
    return false;
  }

  private boolean recordAsFailure(Throwable ignored) {
    // ignoreForBreaker에서 제외한 예외 외에는 응답 파싱 실패까지 출처 실패로 본다.
    return true;
  }

  private boolean ignoreForBreaker(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof PriceProviderException priceException
          && priceException.isSymbolNotFound()) {
        return true;
      }
      if (current instanceof RestClientResponseException responseException) {
        return responseException.getStatusCode().is4xxClientError()
            && responseException.getStatusCode().value() != 429;
      }
      current = current.getCause();
    }
    return false;
  }
}
