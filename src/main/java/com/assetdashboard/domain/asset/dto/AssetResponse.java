package com.assetdashboard.domain.asset.dto;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.entity.AssetType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 자산 단건 응답 (PRD 4-2).
 *
 * <p>엔티티를 그대로 직렬화하지 않는다. {@code userId}, {@code version}, {@code deletedAt} 처럼 클라이언트가
 * 알 필요 없는 필드가 있고, 엔티티를 노출하면 필드를 추가할 때마다 의도치 않게 밖으로 새어나간다(PRD 2장).
 *
 * @param id 자산 id
 * @param type 자산 종류
 * @param symbol 시세 조회 키
 * @param name 표시용 이름
 * @param quantity 보유 수량
 * @param avgPrice 평균 매입 단가 (KRW)
 * @param currentPrice 현재가 (원래 통화 기준)
 * @param currency 통화 코드
 * @param exchangeRate 현재 환율
 * @param valuationKRW 평가금액 (KRW). 현재가가 없으면 null
 * @param realizedPnl 누적 실현손익 (KRW)
 * @param source 현재가의 출처
 * @param priceUpdatedAt 마지막 시세 갱신 시각
 */
public record AssetResponse(
    Long id,
    AssetType type,
    String symbol,
    String name,
    BigDecimal quantity,
    BigDecimal avgPrice,
    BigDecimal currentPrice,
    String currency,
    BigDecimal exchangeRate,
    BigDecimal valuationKRW,
    BigDecimal realizedPnl,
    AssetSource source,
    LocalDateTime priceUpdatedAt) {

  /**
   * 엔티티를 응답 DTO 로 변환한다.
   *
   * @param asset 변환할 자산
   * @return 자산 응답
   */
  public static AssetResponse from(Asset asset) {
    return new AssetResponse(
        asset.getId(),
        asset.getType(),
        asset.getSymbol(),
        asset.getName(),
        asset.getQuantity(),
        asset.getAvgPrice(),
        asset.getCurrentPrice(),
        asset.getCurrency(),
        asset.getExchangeRate(),
        asset.getValuation(),
        asset.getRealizedPnl(),
        asset.getSource(),
        asset.getPriceUpdatedAt());
  }
}
