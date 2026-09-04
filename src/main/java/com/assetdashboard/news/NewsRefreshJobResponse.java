package com.assetdashboard.news;

import java.time.Instant;

public record NewsRefreshJobResponse(
    String jobId,
    String sourceKey,
    NewsRefreshStatus status,
    Instant startedAt,
    Instant completedAt,
    int discoveredCount,
    int insertedCount,
    int updatedCount,
    int unchangedCount,
    String errorCode,
    boolean reused) {

  public static NewsRefreshJobResponse from(NewsRefreshJob job, boolean reused) {
    return new NewsRefreshJobResponse(
        job.getJobId(),
        job.getSourceKey(),
        job.getStatus(),
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getDiscoveredCount(),
        job.getInsertedCount(),
        job.getUpdatedCount(),
        job.getUnchangedCount(),
        job.getErrorCode(),
        reused);
  }
}
