package com.assetdashboard.news;

import java.time.Instant;
import java.util.Set;

/** 외부 수집 Adapter가 공용 저장 계층에 전달하는 정규화된 자료. */
public record CollectedNewsItem(
    String sourceKey,
    String externalId,
    NewsCategory category,
    NewsSourceType sourceType,
    NewsTrust trust,
    String title,
    String publisher,
    String sourceUrl,
    Instant publishedAt,
    Instant fetchedAt,
    String excerpt,
    String content,
    String contentHash,
    Set<String> symbols,
    Set<String> topics) {

  public CollectedNewsItem {
    symbols = Set.copyOf(symbols);
    topics = Set.copyOf(topics);
  }
}
