package com.assetdashboard.news;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 외부 ID로 공용 자료를 멱등 저장해 사용자 수와 무관하게 한 건만 유지한다. */
@Service
@RequiredArgsConstructor
public class NewsIngestionService {

  private final NewsItemRepository newsItemRepository;

  @Transactional
  public NewsIngestionResult ingest(String sourceKey, List<CollectedNewsItem> items) {
    int inserted = 0;
    int updated = 0;
    int unchanged = 0;
    for (CollectedNewsItem item : items) {
      if (!sourceKey.equals(item.sourceKey())) {
        throw new IllegalArgumentException("수집 결과의 sourceKey가 요청 출처와 다릅니다.");
      }
      java.util.Optional<NewsItem> existing =
          newsItemRepository.findBySourceKeyAndExternalId(sourceKey, item.externalId());
      if (existing.isEmpty()) {
        newsItemRepository.save(NewsItem.create(item));
        inserted++;
      } else if (existing.get().refresh(item)) {
        updated++;
      } else {
        unchanged++;
      }
    }
    return new NewsIngestionResult(items.size(), inserted, updated, unchanged);
  }
}
