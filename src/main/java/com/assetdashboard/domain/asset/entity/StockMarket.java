package com.assetdashboard.domain.asset.entity;

/**
 * 주식의 시장과 Yahoo Finance 심볼 접미사를 표현한다.
 *
 * <p>사용자는 종목코드와 시장만 입력하고, 외부 시세 제공자용 심볼은 이 값으로 조합한다.
 */
public enum StockMarket {

  /** 한국거래소 유가증권시장(KOSPI). */
  KOSPI(".KS"),

  /** 코스닥시장(KOSDAQ). */
  KOSDAQ(".KQ"),

  /** 해외 주식. Yahoo Finance 티커를 그대로 사용한다. */
  OVERSEAS("");

  private final String yahooSuffix;

  StockMarket(String yahooSuffix) {
    this.yahooSuffix = yahooSuffix;
  }

  /**
   * Yahoo Finance 조회용 접미사를 반환한다.
   *
   * @return Yahoo Finance 심볼 접미사
   */
  public String yahooSuffix() {
    return yahooSuffix;
  }
}
