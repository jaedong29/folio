package com.assetdashboard.domain.asset.dto;

import com.assetdashboard.infra.price.history.PriceHistoryPoint;
import com.assetdashboard.infra.price.history.PriceHistoryQuote;
import java.time.LocalDateTime;
import java.util.List;

/** 자산 상세의 최근 7일 시장가격 차트 응답. */
public record PriceHistoryResponse(
    String currency,
    String source,
    LocalDateTime fetchedAt,
    boolean stale,
    boolean available,
    List<PriceHistoryPoint> points) {

  public static PriceHistoryResponse from(String currency, PriceHistoryQuote quote) {
    return new PriceHistoryResponse(
        currency,
        quote.source(),
        quote.fetchedAt(),
        quote.stale(),
        quote.available(),
        quote.points());
  }
}
