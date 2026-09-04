package com.assetdashboard.news;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsSourceStatusService {

  private final NewsSourceRegistry sourceRegistry;
  private final NewsRefreshJobRepository jobRepository;

  @Transactional(readOnly = true)
  public List<NewsSourceStatusResponse> getStatuses() {
    return sourceRegistry.all().stream()
        .map(
            source -> {
              NewsRefreshJob latest =
                  jobRepository.findFirstBySourceKeyOrderByCreatedAtDesc(source.key()).orElse(null);
              return new NewsSourceStatusResponse(
                  source.key(),
                  source.displayName(),
                  source.category(),
                  source.symbols(),
                  latest == null ? null : latest.getStatus(),
                  latest == null ? null : latest.getCompletedAt(),
                  latest == null ? null : latest.getErrorCode());
            })
        .toList();
  }
}
