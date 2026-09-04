package com.assetdashboard.news;

import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 짧은 DB 트랜잭션으로 작업 상태만 변경하고 외부 HTTP 대기는 포함하지 않는다. */
@Service
@RequiredArgsConstructor
public class NewsRefreshJobLifecycleService {

  private final NewsRefreshJobRepository jobRepository;
  private final Clock clock;

  @Transactional
  public java.util.Optional<ClaimedNewsRefreshJob> claimNext() {
    return jobRepository
        .findNextForUpdate(NewsRefreshStatus.PENDING, PageRequest.of(0, 1))
        .stream()
        .findFirst()
        .map(
            job -> {
              job.start(Instant.now(clock));
              return new ClaimedNewsRefreshJob(job.getId(), job.getJobId(), job.getSourceKey());
            });
  }

  @Transactional
  public void complete(Long id, NewsIngestionResult result) {
    NewsRefreshJob job =
        jobRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.NEWS_REFRESH_JOB_NOT_FOUND));
    job.complete(Instant.now(clock), result);
  }

  @Transactional
  public void fail(Long id, String safeCode) {
    NewsRefreshJob job =
        jobRepository
            .findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.NEWS_REFRESH_JOB_NOT_FOUND));
    job.fail(Instant.now(clock), safeCode);
  }

  @Transactional
  public void recoverInterrupted() {
    jobRepository.findAllByStatus(NewsRefreshStatus.RUNNING).forEach(NewsRefreshJob::retryInterrupted);
  }

  @Transactional(readOnly = true)
  public NewsRefreshJobResponse get(String jobId) {
    NewsRefreshJob job =
        jobRepository
            .findByJobId(jobId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NEWS_REFRESH_JOB_NOT_FOUND));
    return NewsRefreshJobResponse.from(job, false);
  }
}
