package com.assetdashboard.news;

import java.time.Instant;
import java.util.Set;

/** 뉴스 화면에는 원문 전체 대신 출처·요약 조각·태그만 노출한다. */
public record NewsItemResponse(
    Long id,
    NewsCategory category,
    NewsSourceType sourceType,
    NewsTrust trust,
    String title,
    String publisher,
    String sourceUrl,
    Instant publishedAt,
    Instant fetchedAt,
    String excerpt,
    NewsSummaryStatus summaryStatus,
    String summaryKo,
    String significanceKo,
    String summaryModel,
    String summaryPromptVersion,
    Instant summaryUpdatedAt,
    Set<String> symbols,
    Set<String> topics,
    boolean untrustedContent) {

  public static NewsItemResponse from(NewsItem item) {
    return new NewsItemResponse(
        item.getId(),
        item.getCategory(),
        item.getSourceType(),
        item.getTrust(),
        item.getTitle(),
        item.getPublisher(),
        item.getSourceUrl(),
        item.getPublishedAt(),
        item.getFetchedAt(),
        item.getExcerpt(),
        item.getSummaryStatus(),
        item.getSummaryKo(),
        item.getSignificanceKo(),
        item.getSummaryModel(),
        item.getSummaryPromptVersion(),
        item.getSummaryUpdatedAt(),
        Set.copyOf(item.getSymbols()),
        Set.copyOf(item.getTopics()),
        true);
  }
}
