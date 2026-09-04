package com.assetdashboard.news;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공용 피드와 내 활성 투자자산 관련 피드를 같은 저장소에서 조회한다. */
@Service
@RequiredArgsConstructor
public class NewsQueryService {

  private static final int DEFAULT_LIMIT = 30;
  private static final int MAX_LIMIT = 100;

  private final NewsItemRepository newsItemRepository;
  private final AssetRepository assetRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public NewsFeedResponse getFeed(
      Long userId, NewsScope scope, NewsCategory category, String rawQuery, Integer requestedLimit) {
    NewsScope resolvedScope = scope == null ? NewsScope.ALL : scope;
    int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "뉴스 조회 개수는 1개 이상 100개 이하여야 합니다.");
    }
    String query = normalizeQuery(rawQuery);
    List<String> portfolioSymbols =
        assetRepository.findAllByUserIdAndDeletedAtIsNull(userId).stream()
            .filter(asset -> asset.getType().isInvestment())
            .map(Asset::getSymbol)
            .distinct()
            .sorted()
            .toList();

    List<NewsItem> items;
    if (resolvedScope == NewsScope.PORTFOLIO) {
      items =
          portfolioSymbols.isEmpty()
              ? List.of()
              : newsItemRepository.searchPortfolio(
                  portfolioSymbols, category, query, PageRequest.of(0, limit));
    } else {
      items = newsItemRepository.searchAll(category, query, PageRequest.of(0, limit));
    }
    return new NewsFeedResponse(
        resolvedScope,
        category,
        portfolioSymbols,
        items.stream().map(NewsItemResponse::from).toList(),
        Instant.now(clock));
  }

  private String normalizeQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return null;
    }
    String value = rawQuery.trim();
    if (value.length() < 2 || value.length() > 100) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "뉴스 검색어는 2자 이상 100자 이하로 입력해주세요.");
    }
    return value;
  }
}
