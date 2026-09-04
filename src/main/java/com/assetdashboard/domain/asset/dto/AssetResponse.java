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
 * @param symbol 시세 조회에 사용하는 불변 키
 * @param displaySymbol 사용자 화면에 보여줄 심볼
 * @param name 표시용 이름
 * @param marketLabel 시장 또는 가격 출처 표시명
 * @param defaultSettlementAsset 가입 시 자동 준비된 KRW/USD/USDT 대기자금이면 true
 * @param quantity 보유 수량
 * @param avgPrice 평균 매입 단가 (KRW)
 * @param avgPriceOriginal 평균 매입 단가 (원래 통화)
 * @param currentPrice 현재가 (원래 통화 기준)
 * @param currency 통화 코드
 * @param exchangeRate 현재 환율
 * @param exchangeRateMissing 외화 자산의 현재 환율을 아직 입력하지 않았으면 true
 * @param exchangeRateUpdatedAt 현재 환율 마지막 갱신 시각
 * @param valuationKRW 평가금액 (KRW). 현재가가 없으면 null
 * @param unrealizedPnl 평가손익. 평단을 모르면 null
 * @param unrealizedPnlRate 평가손익률. 평단을 모르면 null
 * @param costBasisMissing 보유 수량은 있지만 평단을 입력하지 않았으면 true
 * @param realizedPnl 누적 실현손익 (KRW)
 * @param source 현재가의 출처
 * @param priceUpdatedAt 마지막 시세 갱신 시각
 */
public record AssetResponse(
    Long id,
    AssetType type,
    String symbol,
    String displaySymbol,
    String name,
    String marketLabel,
    boolean defaultSettlementAsset,
    BigDecimal quantity,
    BigDecimal avgPrice,
    BigDecimal avgPriceOriginal,
    BigDecimal currentPrice,
    String currency,
    BigDecimal exchangeRate,
    boolean exchangeRateMissing,
    LocalDateTime exchangeRateUpdatedAt,
    BigDecimal valuationKRW,
    BigDecimal unrealizedPnl,
    BigDecimal unrealizedPnlRate,
    boolean costBasisMissing,
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
        asset.getDisplaySymbol(),
        asset.getName(),
        asset.getMarketLabel(),
        asset.isDefaultSettlementAsset(),
        asset.getQuantity(),
        asset.getAvgPrice(),
        asset.getAvgPriceOriginal(),
        asset.getCurrentPrice(),
        asset.getCurrency(),
        asset.getCurrentExchangeRate(),
        asset.isValuationBlockedByExchangeRate(),
        asset.getExchangeRateUpdatedAt(),
        asset.getValuation(),
        asset.getUnrealizedPnl(),
        asset.getPnlRate(),
        asset.getQuantity().compareTo(BigDecimal.ZERO) > 0 && asset.getAvgPrice() == null,
        asset.getRealizedPnl(),
        asset.getSource(),
        asset.getPriceUpdatedAt());
  }
}
