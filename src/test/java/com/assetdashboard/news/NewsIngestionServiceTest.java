package com.assetdashboard.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsIngestionServiceTest {

  @Mock private NewsItemRepository repository;
  private NewsIngestionService service;

  @BeforeEach
  void setUp() {
    service = new NewsIngestionService(repository);
  }

  @Test
  void insertsPreviouslyUnknownRelease() {
    CollectedNewsItem item = item("hash-1");
    when(repository.findBySourceKeyAndExternalId("SOURCE", "release-1"))
        .thenReturn(Optional.empty());

    NewsIngestionResult result = service.ingest("SOURCE", List.of(item));

    assertThat(result.insertedCount()).isEqualTo(1);
    verify(repository).save(any(NewsItem.class));
  }

  @Test
  void refreshesSameExternalIdInsteadOfDuplicatingIt() {
    NewsItem existing = NewsItem.create(item("hash-old"));
    when(repository.findBySourceKeyAndExternalId("SOURCE", "release-1"))
        .thenReturn(Optional.of(existing));

    NewsIngestionResult result = service.ingest("SOURCE", List.of(item("hash-new")));

    assertThat(result.updatedCount()).isEqualTo(1);
    assertThat(result.insertedCount()).isZero();
    assertThat(existing.getContentHash()).isEqualTo("hash-new");
  }

  private CollectedNewsItem item(String hash) {
    return new CollectedNewsItem(
        "SOURCE",
        "release-1",
        NewsCategory.CRYPTO,
        NewsSourceType.OFFICIAL_RELEASE,
        NewsTrust.VERIFIED_OFFICIAL,
        "Zebra release",
        "Zcash Foundation",
        "https://github.com/ZcashFoundation/zebra/releases/tag/v1",
        Instant.parse("2026-09-01T00:00:00Z"),
        Instant.parse("2026-09-03T00:00:00Z"),
        "release excerpt",
        "release content",
        hash,
        Set.of("ZEC"),
        Set.of("RELEASE"));
  }
}
