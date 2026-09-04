package com.assetdashboard.news;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 짧은 DB 트랜잭션으로 요약 작업을 claim하고 결과 상태만 반영한다. */
@Service
@RequiredArgsConstructor
public class NewsSummaryLifecycleService {

  public static final String PROMPT_VERSION = "news-summary-v1";

  private final NewsItemRepository repository;
  private final Clock clock;

  @Transactional
  public synchronized Optional<ClaimedNewsSummary> claimNext() {
    Optional<NewsItem> candidate =
        repository.findFirstBySummaryStatusOrderByPublishedAtDescIdDesc(
            NewsSummaryStatus.PENDING);
    if (candidate.isEmpty() || !candidate.get().claimSummary()) {
      return Optional.empty();
    }
    NewsItem item = candidate.get();
    return Optional.of(
        new ClaimedNewsSummary(
            item.getId(),
            item.getContentHash(),
            item.getTitle(),
            item.getPublisher(),
            item.getContent()));
  }

  @Transactional
  public boolean complete(ClaimedNewsSummary claimed, NewsSummaryDraft draft) {
    return repository
        .findById(claimed.newsItemId())
        .map(
            item ->
                item.completeSummary(
                    claimed.contentHash(), draft, PROMPT_VERSION, Instant.now(clock)))
        .orElse(false);
  }

  @Transactional
  public boolean fail(ClaimedNewsSummary claimed, String errorCode) {
    return fail(claimed, errorCode, null);
  }

  @Transactional
  public boolean fail(
      ClaimedNewsSummary claimed, String errorCode, NewsSummaryDraft rejectedDraft) {
    return repository
        .findById(claimed.newsItemId())
        .map(
            item ->
                item.failSummary(
                    claimed.contentHash(),
                    errorCode,
                    rejectedDraft,
                    rejectedDraft == null ? null : PROMPT_VERSION,
                    Instant.now(clock)))
        .orElse(false);
  }

  @Transactional
  public void recoverInterrupted() {
    repository.findAllBySummaryStatus(NewsSummaryStatus.RUNNING)
        .forEach(NewsItem::recoverInterruptedSummary);
  }
}
