package com.assetdashboard.infra.price.history;

import java.time.LocalDateTime;
import java.util.List;

/** 자산 상세 화면에 보여줄 최근 시장가격 시계열. */
public record PriceHistoryQuote(
    String source,
    LocalDateTime fetchedAt,
    boolean stale,
    List<PriceHistoryPoint> points) {

  public PriceHistoryQuote {
    points = List.copyOf(points);
  }

  public boolean available() {
    return !points.isEmpty();
  }

  public boolean isExpired(long ttlMinutes) {
    return fetchedAt == null || fetchedAt.isBefore(LocalDateTime.now().minusMinutes(ttlMinutes));
  }

  public PriceHistoryQuote asStale() {
    return new PriceHistoryQuote(source, fetchedAt, true, points);
  }
}
