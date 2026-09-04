package com.assetdashboard.evidence.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LlmUsageBudgetServiceTest {

  private final LlmDailyUsageRepository repository = mock(LlmDailyUsageRepository.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);
  private final LocalDate today = LocalDate.now(clock);

  @Test
  void skipsRepositoryLookupWhenNoLimitIsConfigured() {
    LlmUsageBudgetService service = service(0, 0);

    service.ensureWithinBudget();

    verifyNoInteractions(repository);
  }

  @Test
  void allowsCallsUnderBothLimits() {
    when(repository.findById(today)).thenReturn(Optional.of(new LlmDailyUsage(today, 5, 1000, 500)));
    LlmUsageBudgetService service = service(200, 200_000);

    service.ensureWithinBudget();
  }

  @Test
  void blocksWhenDailyCallLimitReached() {
    when(repository.findById(today))
        .thenReturn(Optional.of(new LlmDailyUsage(today, 200, 1000, 1000)));
    LlmUsageBudgetService service = service(200, 0);

    assertThatThrownBy(service::ensureWithinBudget)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_BUDGET_EXCEEDED);
  }

  @Test
  void blocksWhenDailyTokenLimitReached() {
    when(repository.findById(today))
        .thenReturn(Optional.of(new LlmDailyUsage(today, 1, 150_000, 60_000)));
    LlmUsageBudgetService service = service(0, 200_000);

    assertThatThrownBy(service::ensureWithinBudget)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_BUDGET_EXCEEDED);
  }

  @Test
  void treatsMissingTodayRowAsZeroUsage() {
    when(repository.findById(today)).thenReturn(Optional.empty());
    LlmUsageBudgetService service = service(200, 200_000);

    service.ensureWithinBudget();
  }

  @Test
  void incrementsExistingRowWithoutReinserting() {
    when(repository.existsById(today)).thenReturn(true);
    LlmUsageBudgetService service = service(0, 0);

    service.recordUsage(120, 45);

    verify(repository).increment(today, 120, 45);
    verify(repository, never()).save(any());
  }

  @Test
  void createsTodayRowBeforeFirstIncrement() {
    when(repository.existsById(today)).thenReturn(false);
    LlmUsageBudgetService service = service(0, 0);

    service.recordUsage(10, 5);

    verify(repository).save(any(LlmDailyUsage.class));
    verify(repository).increment(eq(today), eq(10L), eq(5L));
  }

  private LlmUsageBudgetService service(int callLimit, long tokenLimit) {
    FinancialAgentProperties properties =
        new FinancialAgentProperties(
            true, "https://nim.test/v1", "test-key", "model", 1000, 1000, false, callLimit,
            tokenLimit);
    return new LlmUsageBudgetService(repository, properties, clock);
  }
}
