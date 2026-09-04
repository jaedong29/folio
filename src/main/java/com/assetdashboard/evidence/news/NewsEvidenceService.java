package com.assetdashboard.evidence.news;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.news.NewsItem;
import com.assetdashboard.news.NewsItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsEvidenceService {

  private static final int AGENT_NEWS_LIMIT = 5;

  private final AssetService assetService;
  private final NewsItemRepository newsItemRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public NewsEvidenceResponse getLatest(Long userId, Long assetId) {
    Asset asset = assetService.getOwnedAsset(userId, assetId);
    if (!asset.getType().isInvestment()) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "공용 뉴스는 STOCK/CRYPTO 자산에 대해서만 조회할 수 있습니다.");
    }
    List<NewsItem> items =
        newsItemRepository.findLatestBySymbol(
            asset.getSymbol(), PageRequest.of(0, AGENT_NEWS_LIMIT));
    boolean available = !items.isEmpty();
    return new NewsEvidenceResponse(
        UUID.randomUUID().toString(),
        assetId,
        asset.getSymbol(),
        items.size(),
        available ? EvidenceConclusion.PARTIAL : EvidenceConclusion.UNAVAILABLE,
        Instant.now(clock),
        items.stream().map(NewsEvidenceItem::from).toList(),
        available
            ? List.of("뉴스와 가격 변동의 인과관계는 확인할 수 없습니다.")
            : List.of("등록된 공용 뉴스 근거가 없습니다."));
  }
}
