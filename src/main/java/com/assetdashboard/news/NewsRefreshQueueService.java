package com.assetdashboard.news;

import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 동일 출처의 동시·반복 요청을 재사용해 외부 호출 비용 폭증을 막는다. */
@Service
@RequiredArgsConstructor
public class NewsRefreshQueueService {

  private static final List<NewsRefreshStatus> ACTIVE_STATUSES =
      List.of(NewsRefreshStatus.PENDING, NewsRefreshStatus.RUNNING);

  private final NewsRefreshJobRepository jobRepository;
  private final NewsSourceRegistry sourceRegistry;
  private final NewsProperties properties;
  private final Clock clock;

  @Transactional
  public synchronized NewsRefreshJobResponse enqueue(String sourceKey) {
    sourceRegistry.require(sourceKey);
    if (!properties.externalEnabled()) {
      throw new BusinessException(ErrorCode.NEWS_COLLECTION_DISABLED);
    }
    java.util.Optional<NewsRefreshJob> active =
        jobRepository.findFirstBySourceKeyAndStatusInOrderByCreatedAtDesc(
            sourceKey, ACTIVE_STATUSES);
    if (active.isPresent()) {
      return NewsRefreshJobResponse.from(active.get(), true);
    }

    Instant freshAfter =
        Instant.now(clock).minus(Duration.ofMinutes(properties.refreshCooldownMinutes()));
    java.util.Optional<NewsRefreshJob> latestSuccess =
        jobRepository.findFirstBySourceKeyAndStatusOrderByCompletedAtDesc(
            sourceKey, NewsRefreshStatus.COMPLETED);
    if (latestSuccess.isPresent()
        && latestSuccess.get().getCompletedAt() != null
        && latestSuccess.get().getCompletedAt().isAfter(freshAfter)) {
      return NewsRefreshJobResponse.from(latestSuccess.get(), true);
    }

    return NewsRefreshJobResponse.from(
        jobRepository.save(NewsRefreshJob.pending(sourceKey)), false);
  }
}
