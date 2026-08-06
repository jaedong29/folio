package com.assetdashboard.domain.asset.entity;

/**
 * 자산의 종류.
 *
 * <p>{@code investment} 플래그로 "시세 조회 대상이자 Portfolio 에 노출되는 자산"인지를 구분한다. 화면과 서비스 곳곳에
 * {@code type == STOCK || type == CRYPTO} 라는 조건이 흩어지는 것을 막기 위해 Enum 이 스스로 답하게 했다.
 */
public enum AssetType {

  /** 현금. 보유 금액이 곧 수량이며 currentPrice 는 1로 고정된다. */
  CASH(false),

  /** 은행 예금. CASH 와 동일한 계산 규칙을 따른다. */
  BANK(false),

  /** 주식. Yahoo Finance 로 시세를 조회한다. */
  STOCK(true),

  /** 암호화폐. Binance 가격 × Upbit KRW-USDT 환율로 시세를 계산한다. */
  CRYPTO(true);

  private final boolean investment;

  AssetType(boolean investment) {
    this.investment = investment;
  }

  /**
   * 투자 자산인지 여부를 반환한다.
   *
   * @return STOCK 또는 CRYPTO 이면 {@code true}
   */
  public boolean isInvestment() {
    return investment;
  }

  /**
   * 현금성 자산인지 여부를 반환한다.
   *
   * @return CASH 또는 BANK 이면 {@code true}
   */
  public boolean isCashLike() {
    return !investment;
  }
}
