package com.assetdashboard.domain.asset.dto;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Portfolio 화면의 자산 한 줄 (PRD 4-5).
 *
 * @param assetId 자산 id
 * @param symbol 시세 조회 키
 * @param name 표시 이름
 * @param type 자산 종류
 * @param quantity 보유 수량
 * @param avgPrice 평균 매입 단가 (KRW)
 * @param currentPrice 현재가 (원래 통화 기준)
 * @param currency 통화 코드
 * @param exchangeRate 현재 환율
 * @param valuationKRW 평가금액 (KRW). 현재가를 확보하지 못했으면 null
 * @param unrealizedPnl 평가손익 (KRW)
 * @param unrealizedPnlRate 평가손익률(%). <b>매입금액이 0이면 null</b> — 프론트는 {@code -} 로 표시한다
 * @param realizedPnl 누적 실현손익 (KRW)
 * @param priceStale 오래된 시세를 쓰고 있으면 true. 화면에 "N분 전 기준"을 표시한다
 * @param priceUpdatedAt 마지막 시세 갱신 시각
 */
public record PortfolioResponse(
    Long assetId,
    String symbol,
    String name,
    AssetType type,
    BigDecimal quantity,
    BigDecimal avgPrice,
    BigDecimal currentPrice,
    String currency,
    BigDecimal exchangeRate,
    BigDecimal valuationKRW,
    BigDecimal unrealizedPnl,
    BigDecimal unrealizedPnlRate,
    BigDecimal realizedPnl,
    boolean priceStale,
    LocalDateTime priceUpdatedAt) {

  /**
   * 자산을 Portfolio 응답으로 변환한다.
   *
   * @param asset 변환할 자산
   * @param priceTtlMinutes 시세 유효 시간(분). 이 시간을 넘으면 {@code priceStale} 이 true 가 된다
   * @return Portfolio 응답
   */
  public static PortfolioResponse from(Asset asset, long priceTtlMinutes) {
    return new PortfolioResponse(
        asset.getId(),
        asset.getSymbol(),
        asset.getName(),
        asset.getType(),
        asset.getQuantity(),
        asset.getAvgPrice(),
        asset.getCurrentPrice(),
        asset.getCurrency(),
        asset.getExchangeRate(),
        asset.getValuation(),
        asset.getUnrealizedPnl(),
        asset.getPnlRate(),
        asset.getRealizedPnl(),
        asset.isPriceStale(priceTtlMinutes),
        asset.getPriceUpdatedAt());
  }
}
