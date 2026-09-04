package com.assetdashboard.evidence.news;

import com.assetdashboard.news.NewsCategory;
import com.assetdashboard.news.NewsItem;
import com.assetdashboard.news.NewsSourceType;
import com.assetdashboard.news.NewsTrust;
import java.time.Instant;
import java.util.Set;

/** 모델에는 출처가 확인된 메타데이터와 제한된 인용 조각만 전달한다. */
public record NewsEvidenceItem(
    Long newsId,
    NewsCategory category,
    NewsSourceType sourceType,
    NewsTrust trust,
    String title,
    String publisher,
    String sourceUrl,
    Instant publishedAt,
    String excerpt,
    Set<String> topics,
    boolean untrustedContent) {

  static NewsEvidenceItem from(NewsItem item) {
    return new NewsEvidenceItem(
        item.getId(),
        item.getCategory(),
        item.getSourceType(),
        item.getTrust(),
        item.getTitle(),
        item.getPublisher(),
        item.getSourceUrl(),
        item.getPublishedAt(),
        item.getExcerpt(),
        Set.copyOf(item.getTopics()),
        true);
  }
}
