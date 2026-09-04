package com.assetdashboard.news;

import com.assetdashboard.global.common.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 외부 호출을 HTTP 요청과 분리하기 위한 재시도 가능한 수집 작업. */
@Getter
@Entity
@Table(
    name = "news_refresh_jobs",
    uniqueConstraints = @UniqueConstraint(name = "uk_news_refresh_job", columnNames = "job_id"),
    indexes = {
      @Index(name = "idx_news_refresh_source_created", columnList = "source_key, created_at"),
      @Index(name = "idx_news_refresh_status_created", columnList = "status, created_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsRefreshJob extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "job_id", nullable = false, length = 36)
  private String jobId;

  @Column(name = "source_key", nullable = false, length = 80)
  private String sourceKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NewsRefreshStatus status;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "discovered_count", nullable = false)
  private int discoveredCount;

  @Column(name = "inserted_count", nullable = false)
  private int insertedCount;

  @Column(name = "updated_count", nullable = false)
  private int updatedCount;

  @Column(name = "unchanged_count", nullable = false)
  private int unchangedCount;

  @Column(name = "error_code", length = 80)
  private String errorCode;

  @Version
  @Column(nullable = false)
  private Long version;

  private NewsRefreshJob(String sourceKey) {
    this.jobId = UUID.randomUUID().toString();
    this.sourceKey = sourceKey;
    this.status = NewsRefreshStatus.PENDING;
  }

  public static NewsRefreshJob pending(String sourceKey) {
    return new NewsRefreshJob(sourceKey);
  }

  public void start(Instant startedAt) {
    this.status = NewsRefreshStatus.RUNNING;
    this.startedAt = startedAt;
    this.errorCode = null;
  }

  public void complete(Instant completedAt, NewsIngestionResult result) {
    this.status = NewsRefreshStatus.COMPLETED;
    this.completedAt = completedAt;
    this.discoveredCount = result.discoveredCount();
    this.insertedCount = result.insertedCount();
    this.updatedCount = result.updatedCount();
    this.unchangedCount = result.unchangedCount();
    this.errorCode = null;
  }

  public void fail(Instant completedAt, String safeErrorCode) {
    this.status = NewsRefreshStatus.FAILED;
    this.completedAt = completedAt;
    this.errorCode = safeErrorCode;
  }

  /** 프로세스 종료 중 끊긴 외부 호출은 멱등 수집 작업으로 다시 실행할 수 있다. */
  public void retryInterrupted() {
    this.status = NewsRefreshStatus.PENDING;
    this.startedAt = null;
    this.completedAt = null;
    this.errorCode = null;
  }
}
