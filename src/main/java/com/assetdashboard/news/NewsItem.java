package com.assetdashboard.news;

import com.assetdashboard.global.common.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 자산과 독립적으로 한 번만 저장되는 공용 금융 뉴스·공식자료. */
@Getter
@Entity
@Table(
    name = "news_items",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_news_source_external",
            columnNames = {"source_key", "external_id"}),
    indexes = {
      @Index(name = "idx_news_published", columnList = "published_at"),
      @Index(name = "idx_news_category_published", columnList = "category, published_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsItem extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "source_key", nullable = false, length = 80)
  private String sourceKey;

  @Column(name = "external_id", nullable = false, length = 160)
  private String externalId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private NewsCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 32)
  private NewsSourceType sourceType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private NewsTrust trust;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(nullable = false, length = 120)
  private String publisher;

  @Column(name = "source_url", nullable = false, length = 2048)
  private String sourceUrl;

  @Column(name = "published_at", nullable = false)
  private Instant publishedAt;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;

  @Column(length = 1000)
  private String excerpt;

  /** 공식 원문도 Prompt Injection 관점에서는 신뢰하지 않는 인용 데이터다. */
  @Lob
  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String content;

  @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
  private String contentHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "summary_status", nullable = false, length = 20)
  private NewsSummaryStatus summaryStatus;

  @Column(name = "summary_ko", length = 500)
  private String summaryKo;

  @Column(name = "significance_ko", length = 300)
  private String significanceKo;

  @Column(name = "summary_model", length = 120)
  private String summaryModel;

  @Column(name = "summary_prompt_version", length = 40)
  private String summaryPromptVersion;

  @Column(name = "summarized_content_hash", length = 64, columnDefinition = "CHAR(64)")
  private String summarizedContentHash;

  @Column(name = "summary_error_code", length = 80)
  private String summaryErrorCode;

  @Column(name = "summary_latency_ms")
  private Long summaryLatencyMs;

  @Column(name = "summary_input_tokens")
  private Long summaryInputTokens;

  @Column(name = "summary_output_tokens")
  private Long summaryOutputTokens;

  @Column(name = "summary_updated_at")
  private Instant summaryUpdatedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "news_item_symbols",
      joinColumns = @JoinColumn(name = "news_item_id"))
  @Column(name = "symbol", nullable = false, length = 30)
  private Set<String> symbols = new LinkedHashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "news_item_topics",
      joinColumns = @JoinColumn(name = "news_item_id"))
  @Column(name = "topic", nullable = false, length = 40)
  private Set<String> topics = new LinkedHashSet<>();

  private NewsItem(CollectedNewsItem item) {
    apply(item);
    resetSummary();
  }

  public static NewsItem create(CollectedNewsItem item) {
    return new NewsItem(item);
  }

  /** 같은 외부 ID의 제목·본문이 수정된 경우 최신 원문으로 갱신한다. */
  public boolean refresh(CollectedNewsItem item) {
    boolean changed = !contentHash.equals(item.contentHash());
    apply(item);
    if (changed || summaryStatus == NewsSummaryStatus.FAILED) {
      resetSummary();
    }
    return changed;
  }

  public boolean claimSummary() {
    if (summaryStatus != NewsSummaryStatus.PENDING) {
      return false;
    }
    summaryStatus = NewsSummaryStatus.RUNNING;
    summaryErrorCode = null;
    return true;
  }

  public boolean completeSummary(
      String expectedContentHash,
      NewsSummaryDraft draft,
      String promptVersion,
      Instant completedAt) {
    if (summaryStatus != NewsSummaryStatus.RUNNING || !contentHash.equals(expectedContentHash)) {
      return false;
    }
    summaryStatus = NewsSummaryStatus.COMPLETED;
    summaryKo = draft.summaryKo().trim();
    significanceKo = draft.significanceKo().trim();
    summaryModel = draft.model();
    summaryPromptVersion = promptVersion;
    summarizedContentHash = contentHash;
    summaryErrorCode = null;
    summaryLatencyMs = draft.latencyMs();
    summaryInputTokens = draft.inputTokens();
    summaryOutputTokens = draft.outputTokens();
    summaryUpdatedAt = completedAt;
    return true;
  }

  public boolean failSummary(String expectedContentHash, String errorCode, Instant failedAt) {
    if (summaryStatus != NewsSummaryStatus.RUNNING || !contentHash.equals(expectedContentHash)) {
      return false;
    }
    summaryStatus = NewsSummaryStatus.FAILED;
    summaryErrorCode = errorCode;
    summaryUpdatedAt = failedAt;
    return true;
  }

  public void recoverInterruptedSummary() {
    if (summaryStatus == NewsSummaryStatus.RUNNING) {
      summaryStatus = NewsSummaryStatus.PENDING;
      summaryErrorCode = "SUMMARY_INTERRUPTED";
    }
  }

  private void resetSummary() {
    summaryStatus = NewsSummaryStatus.PENDING;
    summaryKo = null;
    significanceKo = null;
    summaryModel = null;
    summaryPromptVersion = null;
    summarizedContentHash = null;
    summaryErrorCode = null;
    summaryLatencyMs = null;
    summaryInputTokens = null;
    summaryOutputTokens = null;
    summaryUpdatedAt = null;
  }

  private void apply(CollectedNewsItem item) {
    this.sourceKey = item.sourceKey();
    this.externalId = item.externalId();
    this.category = item.category();
    this.sourceType = item.sourceType();
    this.trust = item.trust();
    this.title = item.title();
    this.publisher = item.publisher();
    this.sourceUrl = item.sourceUrl();
    this.publishedAt = item.publishedAt();
    this.fetchedAt = item.fetchedAt();
    this.excerpt = item.excerpt();
    this.content = item.content();
    this.contentHash = item.contentHash();
    this.symbols.clear();
    this.symbols.addAll(item.symbols());
    this.topics.clear();
    this.topics.addAll(item.topics());
  }
}
