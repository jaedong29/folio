package com.assetdashboard.evidence.trend;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.trend.PriceTrendEvidenceResponse.TrendWarning;
import com.assetdashboard.infra.price.history.PriceHistoryPoint;
import com.assetdashboard.infra.price.history.PriceHistoryQueryService;
import com.assetdashboard.infra.price.history.PriceHistoryQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 최근 일별 가격을 사용해 방향을 결정적으로 계산하는 읽기 전용 서비스. */
@Service
@RequiredArgsConstructor
public class PriceTrendEvidenceService {

  static final String WINDOW = "RECENT_7_DAILY_POINTS";
  static final BigDecimal DIRECTION_THRESHOLD_PERCENT = new BigDecimal("2.00");
  static final String DIRECTION_RULE =
      "returnRatePercent > 2.00 => UP; returnRatePercent < -2.00 => DOWN; otherwise FLAT";

  private final AssetService assetService;
  private final PriceHistoryQueryService priceHistoryQueryService;

  public PriceTrendEvidenceResponse getPriceTrendEvidence(Long userId, Long assetId) {
    Asset asset = assetService.getOwnedAsset(userId, assetId);
    if (!asset.getType().isInvestment()) {
      return unavailable(
          asset,
          null,
          List.of(new TrendWarning("PRICE_TREND_UNSUPPORTED", "투자 자산만 가격 방향을 확인할 수 있습니다.")));
    }

    PriceHistoryQuote quote =
        priceHistoryQueryService.getHistory(asset.getType(), asset.getSymbol());
    List<PriceHistoryPoint> points =
        quote.points().stream()
            .filter(point -> point.price() != null && point.price().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparingLong(PriceHistoryPoint::timestamp))
            .toList();
    if (points.size() < 2) {
      return unavailable(
          asset,
          quote,
          List.of(new TrendWarning("PRICE_HISTORY_MISSING", "방향 계산에 필요한 가격 이력이 부족합니다.")));
    }

    PriceHistoryPoint start = points.get(0);
    PriceHistoryPoint end = points.get(points.size() - 1);
    BigDecimal returnRate =
        end.price()
            .subtract(start.price())
            .divide(start.price(), 8, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP);
    PriceTrendDirection direction = direction(returnRate);
    List<TrendWarning> warnings =
        quote.stale()
            ? List.of(new TrendWarning("PRICE_HISTORY_STALE", "가격 이력이 갱신 기준 시간을 지났습니다."))
            : List.of();

    return new PriceTrendEvidenceResponse(
        UUID.randomUUID().toString(),
        LocalDateTime.now(),
        quote.stale() ? EvidenceConclusion.PARTIAL : EvidenceConclusion.CONFIRMED,
        asset.getId(),
        asset.getType(),
        asset.getSymbol(),
        asset.getDisplaySymbol(),
        WINDOW,
        points.size(),
        Instant.ofEpochMilli(start.timestamp()),
        Instant.ofEpochMilli(end.timestamp()),
        start.price(),
        end.price(),
        returnRate,
        DIRECTION_THRESHOLD_PERCENT,
        direction,
        DIRECTION_RULE,
        quote.source(),
        quote.fetchedAt(),
        quote.stale(),
        warnings);
  }

  private PriceTrendDirection direction(BigDecimal returnRate) {
    if (returnRate.compareTo(DIRECTION_THRESHOLD_PERCENT) > 0) {
      return PriceTrendDirection.UP;
    }
    if (returnRate.compareTo(DIRECTION_THRESHOLD_PERCENT.negate()) < 0) {
      return PriceTrendDirection.DOWN;
    }
    return PriceTrendDirection.FLAT;
  }

  private PriceTrendEvidenceResponse unavailable(
      Asset asset, PriceHistoryQuote quote, List<TrendWarning> warnings) {
    int pointCount = quote == null ? 0 : quote.points().size();
    return new PriceTrendEvidenceResponse(
        UUID.randomUUID().toString(),
        LocalDateTime.now(),
        EvidenceConclusion.UNAVAILABLE,
        asset.getId(),
        asset.getType(),
        asset.getSymbol(),
        asset.getDisplaySymbol(),
        WINDOW,
        pointCount,
        null,
        null,
        null,
        null,
        null,
        DIRECTION_THRESHOLD_PERCENT,
        PriceTrendDirection.UNAVAILABLE,
        DIRECTION_RULE,
        quote == null ? null : quote.source(),
        quote == null ? null : quote.fetchedAt(),
        quote == null || quote.stale(),
        warnings);
  }
}
