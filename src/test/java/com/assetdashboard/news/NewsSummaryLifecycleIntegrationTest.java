package com.assetdashboard.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class NewsSummaryLifecycleIntegrationTest {

  @Autowired private NewsItemRepository repository;
  @Autowired private NewsSummaryLifecycleService lifecycleService;

  @Test
  void persistsClaimedSummaryAndOperationalMetadata() {
    NewsItem saved = repository.saveAndFlush(NewsItem.create(item()));

    ClaimedNewsSummary claimed = lifecycleService.claimNext().orElseThrow();
    boolean completed =
        lifecycleService.complete(
            claimed,
            new NewsSummaryDraft(
                "DNS 시더가 추가됐습니다.",
                "노드 연결 경로를 보강하는 변경입니다.",
                "nemotron",
                321,
                120,
                30));

    NewsItem reloaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(completed).isTrue();
    assertThat(reloaded.getSummaryStatus()).isEqualTo(NewsSummaryStatus.COMPLETED);
    assertThat(reloaded.getSummaryKo()).isEqualTo("DNS 시더가 추가됐습니다.");
    assertThat(reloaded.getSummaryPromptVersion()).isEqualTo("news-summary-v1");
    assertThat(reloaded.getSummarizedContentHash()).isEqualTo("hash-1");
    assertThat(reloaded.getSummaryLatencyMs()).isEqualTo(321);
    assertThat(reloaded.getSummaryInputTokens()).isEqualTo(120);
    assertThat(reloaded.getSummaryOutputTokens()).isEqualTo(30);
  }

  private CollectedNewsItem item() {
    return new CollectedNewsItem(
        "SUMMARY_TEST_SOURCE",
        "release-1",
        NewsCategory.CRYPTO,
        NewsSourceType.OFFICIAL_RELEASE,
        NewsTrust.VERIFIED_OFFICIAL,
        "Zebra 6.3.0",
        "Zcash Foundation",
        "https://github.com/ZcashFoundation/zebra/releases/tag/v6.3.0",
        Instant.parse("2026-09-01T00:00:00Z"),
        Instant.parse("2026-09-03T00:00:00Z"),
        "DNS seeders were added.",
        "DNS seeders were added.",
        "hash-1",
        Set.of("ZEC"),
        Set.of("RELEASE"));
  }
}
