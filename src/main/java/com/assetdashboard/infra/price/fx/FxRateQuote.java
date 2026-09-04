package com.assetdashboard.infra.price.fx;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 기준 통화 KRW로 환산하기 위한 시장 환율 한 건.
 *
 * @param currency 원래 통화 코드
 * @param krwRate 원/통화 환율
 * @param source 외부 조회 출처
 * @param fetchedAt 값을 확보한 시각
 */
public record FxRateQuote(
    String currency, BigDecimal krwRate, String source, LocalDateTime fetchedAt) {

  /**
   * 환율 응답을 생성한다.
   *
   * @param currency 통화 코드
   * @param krwRate 원/통화 환율
   * @param source 조회 출처
   * @return 현재 시각이 기록된 환율
   */
  public static FxRateQuote of(String currency, BigDecimal krwRate, String source) {
    return new FxRateQuote(currency, krwRate, source, LocalDateTime.now());
  }

  /**
   * TTL 기준으로 만료 여부를 반환한다.
   *
   * @param ttlMinutes 유효 시간(분)
   * @return 만료되었으면 true
   */
  public boolean isExpired(long ttlMinutes) {
    return fetchedAt.isBefore(LocalDateTime.now().minusMinutes(ttlMinutes));
  }
}
