package com.assetdashboard.news;

import com.assetdashboard.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 요청 스레드 밖에서 공식 출처를 조회하고 멱등 저장하는 단일 작업자. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsRefreshWorker {

  private final NewsRefreshJobLifecycleService lifecycleService;
  private final NewsSourceRegistry sourceRegistry;
  private final NewsIngestionService ingestionService;

  @EventListener(ApplicationReadyEvent.class)
  public void recoverInterruptedJobs() {
    lifecycleService.recoverInterrupted();
  }

  @Scheduled(fixedDelayString = "${app.news.worker-delay-millis:1000}")
  public void processNext() {
    java.util.Optional<ClaimedNewsRefreshJob> claimed = lifecycleService.claimNext();
    if (claimed.isEmpty()) {
      return;
    }
    ClaimedNewsRefreshJob job = claimed.get();
    try {
      OfficialNewsSource source = sourceRegistry.require(job.sourceKey());
      List<CollectedNewsItem> items = source.fetch();
      NewsIngestionResult result = ingestionService.ingest(job.sourceKey(), items);
      lifecycleService.complete(job.id(), result);
      log.info(
          "[News] refresh completed jobId={} source={} discovered={} inserted={} updated={}",
          job.jobId(),
          job.sourceKey(),
          result.discoveredCount(),
          result.insertedCount(),
          result.updatedCount());
    } catch (NewsSourceException e) {
      lifecycleService.fail(job.id(), e.safeCode());
      log.warn("[News] refresh failed jobId={} code={}", job.jobId(), e.safeCode());
    } catch (BusinessException e) {
      lifecycleService.fail(job.id(), e.getErrorCode().name());
      log.warn("[News] refresh failed jobId={} code={}", job.jobId(), e.getErrorCode().name());
    } catch (RuntimeException e) {
      lifecycleService.fail(job.id(), "UNEXPECTED_COLLECTION_FAILURE");
      log.error("[News] refresh failed jobId={} type={}", job.jobId(), e.getClass().getSimpleName());
    }
  }
}
