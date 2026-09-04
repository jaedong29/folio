package com.assetdashboard.evidence.agent;

import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * NIM 호출 전에 오늘 누적 사용량을 하루 예산과 비교해 초과 호출을 막는다.
 *
 * <p>Financial Evidence Agent와 News 요약 클라이언트가 이 한 카운터를 공유한다. 한도가 0 이하이면 그
 * 항목은 무제한으로 취급한다. 카운터 증가는 항상 원자적 UPDATE로만 하며, 최초 행 생성 경합은 무시해도
 * 되는 수준(개인 MVP, 저동시성)이라 낙관적 락 없이 처리한다.
 */
@Component
@RequiredArgsConstructor
public class LlmUsageBudgetService {

  private final LlmDailyUsageRepository repository;
  private final FinancialAgentProperties properties;
  private final Clock clock;

  @Transactional(readOnly = true)
  public void ensureWithinBudget() {
    int callLimit = properties.dailyCallLimit();
    long tokenLimit = properties.dailyTokenLimit();
    if (callLimit <= 0 && tokenLimit <= 0) {
      return;
    }
    LlmDailyUsage usage = repository.findById(LocalDate.now(clock)).orElse(null);
    long calls = usage == null ? 0 : usage.getCallCount();
    long tokens = usage == null ? 0 : usage.getInputTokens() + usage.getOutputTokens();
    if (callLimit > 0 && calls >= callLimit) {
      throw new BusinessException(
          ErrorCode.AI_BUDGET_EXCEEDED, "오늘의 NIM 호출 한도(" + callLimit + "회)를 초과했습니다.");
    }
    if (tokenLimit > 0 && tokens >= tokenLimit) {
      throw new BusinessException(
          ErrorCode.AI_BUDGET_EXCEEDED, "오늘의 NIM 토큰 한도(" + tokenLimit + ")를 초과했습니다.");
    }
  }

  @Transactional
  public void recordUsage(long inputTokens, long outputTokens) {
    LocalDate today = LocalDate.now(clock);
    if (!repository.existsById(today)) {
      try {
        repository.save(LlmDailyUsage.startingToday(today));
      } catch (DataIntegrityViolationException ignored) {
        // 동시에 다른 호출이 오늘 첫 행을 먼저 만들었다. 증분만 이어서 적용한다.
      }
    }
    repository.increment(today, Math.max(0, inputTokens), Math.max(0, outputTokens));
  }

  @Transactional(readOnly = true)
  public LlmDailyUsage getTodayUsage() {
    LocalDate today = LocalDate.now(clock);
    return repository.findById(today).orElseGet(() -> LlmDailyUsage.startingToday(today));
  }
}
