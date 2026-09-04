package com.assetdashboard.evidence.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.news.CollectedNewsItem;
import com.assetdashboard.news.NewsCategory;
import com.assetdashboard.news.NewsItem;
import com.assetdashboard.news.NewsItemRepository;
import com.assetdashboard.news.NewsSourceType;
import com.assetdashboard.news.NewsTrust;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NewsEvidenceServiceTest {

  @Mock private AssetService assetService;
  @Mock private NewsItemRepository newsItemRepository;
  private NewsEvidenceService service;

  @BeforeEach
  void setUp() {
    service =
        new NewsEvidenceService(
            assetService,
            newsItemRepository,
            Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC));
    when(assetService.getOwnedAsset(7L, 42L))
        .thenReturn(Asset.create(7L, AssetType.CRYPTO, "ZEC", "Zcash", "USDT"));
  }

  @Test
  void mapsOwnedAssetSymbolToSharedNewsWithoutUserSpecificCopies() {
    when(newsItemRepository.findLatestBySymbol(eq("ZEC"), any(Pageable.class)))
        .thenReturn(List.of(item()));

    NewsEvidenceResponse response = service.getLatest(7L, 42L);

    assertThat(response.symbol()).isEqualTo("ZEC");
    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.PARTIAL);
    assertThat(response.items()).singleElement().satisfies(
        news -> {
          assertThat(news.trust()).isEqualTo(NewsTrust.VERIFIED_OFFICIAL);
          assertThat(news.untrustedContent()).isTrue();
        });
    verify(assetService).getOwnedAsset(7L, 42L);
  }

  @Test
  void returnsUnavailableInsteadOfInventingNews() {
    when(newsItemRepository.findLatestBySymbol(eq("ZEC"), any(Pageable.class)))
        .thenReturn(List.of());

    NewsEvidenceResponse response = service.getLatest(7L, 42L);

    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(response.items()).isEmpty();
  }

  private NewsItem item() {
    return NewsItem.create(
        new CollectedNewsItem(
            "SOURCE",
            "1",
            NewsCategory.CRYPTO,
            NewsSourceType.OFFICIAL_RELEASE,
            NewsTrust.VERIFIED_OFFICIAL,
            "Zebra 3.0.0",
            "Zcash Foundation",
            "https://github.com/ZcashFoundation/zebra/releases/tag/v3.0.0",
            Instant.parse("2026-09-02T00:00:00Z"),
            Instant.parse("2026-09-03T00:00:00Z"),
            "Security release",
            "Security release",
            "hash",
            Set.of("ZEC"),
            Set.of("RELEASE")));
  }
}
