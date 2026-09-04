package com.assetdashboard.infra.price;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 외부에서 조회한 시세 한 건.
 *
 * <p>{@code exchangeRate} 는 <b>조회처가 환율까지 알고 있는 경우에만</b> 채워진다. 시세 제공자가 환율을 주지 않는
 * STOCK 은 {@code null} 이며, 별도의 환율 조회 서비스가 Yahoo Finance의 USD/KRW를 적용한다.
 *
 * @param price 원래 통화 기준 현재가
 * @param exchangeRate 원/통화 환율. 조회처가 제공하지 않으면 null
 * @param fetchedAt 조회 시각
 */
public record PriceQuote(BigDecimal price, BigDecimal exchangeRate, LocalDateTime fetchedAt) {

  /**
   * 환율 없이 가격만 담은 시세를 만든다.
   *
   * @param price 원래 통화 기준 현재가
   * @return 시세
   */
  public static PriceQuote of(BigDecimal price) {
    return new PriceQuote(price, null, LocalDateTime.now());
  }

  /**
   * 가격과 환율을 함께 담은 시세를 만든다.
   *
   * @param price 원래 통화 기준 현재가
   * @param exchangeRate 원/통화 환율
   * @return 시세
   */
  public static PriceQuote of(BigDecimal price, BigDecimal exchangeRate) {
    return new PriceQuote(price, exchangeRate, LocalDateTime.now());
  }

  /**
   * TTL 기준으로 이 시세가 만료되었는지 판단한다.
   *
   * @param ttlMinutes 유효 시간(분)
   * @return 만료되었으면 {@code true}
   */
  public boolean isExpired(long ttlMinutes) {
    return fetchedAt.isBefore(LocalDateTime.now().minusMinutes(ttlMinutes));
  }
}
