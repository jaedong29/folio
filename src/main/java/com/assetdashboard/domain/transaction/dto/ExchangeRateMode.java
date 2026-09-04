package com.assetdashboard.domain.transaction.dto;

/**
 * 거래 환율을 누가 결정하는지 나타낸다.
 *
 * <p>{@link #AUTO}는 서버가 자산에 저장된 현재 환율을 사용한다. {@link #MANUAL}은 사용자가 거래 시점 환율을
 * 직접 확인해 입력했다는 뜻이다. 과거 외화 거래에는 현재 환율을 자동으로 적용할 수 없으므로 MANUAL만 허용한다.
 */
public enum ExchangeRateMode {
  AUTO,
  MANUAL;

  /** 요청에서 생략된 기존 클라이언트는 AUTO로 해석한다. */
  public static ExchangeRateMode defaultIfNull(ExchangeRateMode mode) {
    return mode == null ? AUTO : mode;
  }
}
