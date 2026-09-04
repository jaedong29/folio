package com.assetdashboard.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NewsItemSummaryStateTest {

  @Test
  void unchangedContentKeepsCompletedSummaryButChangedContentRequeuesIt() {
    NewsItem item = NewsItem.create(item("hash-1", "first content"));
    assertThat(item.getSummaryStatus()).isEqualTo(NewsSummaryStatus.PENDING);
    assertThat(item.claimSummary()).isTrue();
    assertThat(
            item.completeSummary(
                "hash-1",
                new NewsSummaryDraft("한국어 요약입니다.", "기술적 의미입니다.", "model", 10, 20, 5),
                "news-summary-v1",
                Instant.parse("2026-09-05T00:00:00Z")))
        .isTrue();

    assertThat(item.refresh(item("hash-1", "first content"))).isFalse();
    assertThat(item.getSummaryStatus()).isEqualTo(NewsSummaryStatus.COMPLETED);
    assertThat(item.getSummaryKo()).isEqualTo("한국어 요약입니다.");

    assertThat(item.refresh(item("hash-2", "changed content"))).isTrue();
    assertThat(item.getSummaryStatus()).isEqualTo(NewsSummaryStatus.PENDING);
    assertThat(item.getSummaryKo()).isNull();
    assertThat(item.getSummarizedContentHash()).isNull();
  }

  @Test
  void staleWorkerResultCannotOverwriteChangedContent() {
    NewsItem item = NewsItem.create(item("hash-1", "first content"));
    item.claimSummary();
    item.refresh(item("hash-2", "changed content"));

    boolean completed =
        item.completeSummary(
            "hash-1",
            new NewsSummaryDraft("이전 요약", "이전 의미", "model", 10, 20, 5),
            "news-summary-v1",
            Instant.parse("2026-09-05T00:00:00Z"));

    assertThat(completed).isFalse();
    assertThat(item.getSummaryStatus()).isEqualTo(NewsSummaryStatus.PENDING);
    assertThat(item.getSummaryKo()).isNull();
  }

  @Test
  void responseExposesOnlyOperationalMetadataForRejectedSummary() {
    NewsItem item = NewsItem.create(item("hash-1", "first content"));
    item.claimSummary();
    NewsSummaryDraft rejected =
        new NewsSummaryDraft("요약입니다.", "가격 상승 예상", "nemotron", 321, 120, 30);

    assertThat(
            item.failSummary(
                "hash-1",
                "SUMMARY_UNSAFE_CLAIM",
                rejected,
                "news-summary-v1",
                Instant.parse("2026-09-05T00:00:00Z")))
        .isTrue();

    NewsItemResponse response = NewsItemResponse.from(item);
    assertThat(response.summaryStatus()).isEqualTo(NewsSummaryStatus.FAILED);
    assertThat(response.summaryKo()).isNull();
    assertThat(response.significanceKo()).isNull();
    assertThat(response.summaryErrorCode()).isEqualTo("SUMMARY_UNSAFE_CLAIM");
    assertThat(response.summaryModel()).isEqualTo("nemotron");
    assertThat(response.summaryPromptVersion()).isEqualTo("news-summary-v1");
    assertThat(response.summaryLatencyMs()).isEqualTo(321);
    assertThat(response.summaryInputTokens()).isEqualTo(120);
    assertThat(response.summaryOutputTokens()).isEqualTo(30);
  }

  private CollectedNewsItem item(String hash, String content) {
    return new CollectedNewsItem(
        "SOURCE",
        "release-1",
        NewsCategory.CRYPTO,
        NewsSourceType.OFFICIAL_RELEASE,
        NewsTrust.VERIFIED_OFFICIAL,
        "Zebra 6.3.0",
        "Zcash Foundation",
        "https://github.com/ZcashFoundation/zebra/releases/tag/v6.3.0",
        Instant.parse("2026-09-01T00:00:00Z"),
        Instant.parse("2026-09-03T00:00:00Z"),
        "excerpt",
        content,
        hash,
        Set.of("ZEC"),
        Set.of("RELEASE"));
  }
}
