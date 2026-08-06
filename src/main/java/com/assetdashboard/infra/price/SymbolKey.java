package com.assetdashboard.infra.price;

import com.assetdashboard.domain.asset.entity.AssetType;

/**
 * 시세 조회와 캐싱의 단위가 되는 키.
 *
 * <p>시세는 사용자별 데이터가 아니라 <b>전역 사실</b>이므로 캐시는 Asset row 가 아니라 이 키에 붙는다. 사용자
 * 100명이 BTC 를 보유해도 BTC 시세는 TTL 당 1번만 외부에서 조회된다(PRD 2장).
 *
 * <p>{@code type} 이 키에 포함되는 이유는 같은 문자열이라도 조회처가 다르기 때문이다 — STOCK 의 {@code BTC} 와
 * CRYPTO 의 {@code BTC} 는 서로 다른 시세다.
 *
 * @param type 자산 종류
 * @param symbol 정규화된 심볼
 */
public record SymbolKey(AssetType type, String symbol) {

  /**
   * 로그·에러 메시지용 표현을 만든다.
   *
   * @return {@code CRYPTO:BTC} 형태의 문자열
   */
  @Override
  public String toString() {
    return type + ":" + symbol;
  }
}
