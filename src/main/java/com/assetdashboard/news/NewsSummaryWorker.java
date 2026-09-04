package com.assetdashboard.news;

import com.assetdashboard.evidence.agent.LlmUsageBudgetService;
import com.assetdashboard.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 수집과 분리해 공용 자료를 한 번만 요약하고 실패 시 원문 fallback을 유지한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsSummaryWorker {

  private final NewsProperties properties;
  private final NewsSummaryLifecycleService lifecycleService;
  private final NewsSummaryModelClient modelClient;
  private final NewsSummaryGuardrail guardrail;
  private final LlmUsageBudgetService budgetService;

  @EventListener(ApplicationReadyEvent.class)
  public void recoverInterruptedJobs() {
    lifecycleService.recoverInterrupted();
  }

  @Scheduled(fixedDelayString = "${app.news.summary-worker-delay-millis:1200}")
  public void processNext() {
    if (!properties.summaryEnabled() || !modelClient.isEnabled()) {
      return;
    }
    Optional<ClaimedNewsSummary> candidate = lifecycleService.claimNext();
    if (candidate.isEmpty()) {
      return;
    }
    ClaimedNewsSummary claimed = candidate.get();
    try {
      budgetService.ensureWithinBudget();
      NewsSummaryDraft draft = modelClient.summarize(claimed);
      budgetService.recordUsage(draft.inputTokens(), draft.outputTokens());
      Optional<String> violation = guardrail.validate(claimed, draft);
      if (violation.isPresent()) {
        lifecycleService.fail(claimed, violation.get(), draft);
        log.warn("[NewsSummary] blocked newsItemId={} code={}", claimed.newsItemId(), violation.get());
        return;
      }
      if (lifecycleService.complete(claimed, draft)) {
        log.info(
            "[NewsSummary] completed newsItemId={} model={} latencyMs={} inputTokens={} outputTokens={}",
            claimed.newsItemId(),
            draft.model(),
            draft.latencyMs(),
            draft.inputTokens(),
            draft.outputTokens());
      } else {
        log.info("[NewsSummary] discarded stale result newsItemId={}", claimed.newsItemId());
      }
    } catch (BusinessException e) {
      lifecycleService.fail(claimed, e.getErrorCode().name());
      log.warn(
          "[NewsSummary] failed newsItemId={} code={}",
          claimed.newsItemId(),
          e.getErrorCode().name());
    } catch (RuntimeException e) {
      lifecycleService.fail(claimed, "UNEXPECTED_SUMMARY_FAILURE");
      log.error(
          "[NewsSummary] failed newsItemId={} type={}",
          claimed.newsItemId(),
          e.getClass().getSimpleName());
    }
  }
}
