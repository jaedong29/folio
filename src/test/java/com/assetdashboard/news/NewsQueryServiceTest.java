package com.assetdashboard.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NewsQueryServiceTest {

  @Mock private NewsItemRepository newsItemRepository;
  @Mock private AssetRepository assetRepository;
  private NewsQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new NewsQueryService(
            newsItemRepository,
            assetRepository,
            Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void portfolioScopeUsesOnlyActiveInvestmentSymbols() {
    Asset zec = Asset.create(7L, AssetType.CRYPTO, "ZEC", "Zcash", "USDT");
    Asset cash = Asset.create(7L, AssetType.CASH, "KRW", "원화", "KRW");
    when(assetRepository.findAllByUserIdAndDeletedAtIsNull(7L)).thenReturn(List.of(zec, cash));
    when(newsItemRepository.searchPortfolio(
            eq(List.of("ZEC")), eq(NewsCategory.CRYPTO), eq(null), org.mockito.ArgumentMatchers.any(Pageable.class)))
        .thenReturn(List.of());

    NewsFeedResponse response =
        service.getFeed(7L, NewsScope.PORTFOLIO, NewsCategory.CRYPTO, null, 30);

    assertThat(response.portfolioSymbols()).containsExactly("ZEC");
    verify(newsItemRepository)
        .searchPortfolio(
            eq(List.of("ZEC")),
            eq(NewsCategory.CRYPTO),
            eq(null),
            org.mockito.ArgumentMatchers.any(Pageable.class));
  }

  @Test
  void emptyPortfolioReturnsNoItemsWithoutBroadeningToGlobalFeed() {
    when(assetRepository.findAllByUserIdAndDeletedAtIsNull(7L)).thenReturn(List.of());

    NewsFeedResponse response =
        service.getFeed(7L, NewsScope.PORTFOLIO, null, null, null);

    assertThat(response.items()).isEmpty();
    verify(newsItemRepository, never())
        .searchAll(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }
}
