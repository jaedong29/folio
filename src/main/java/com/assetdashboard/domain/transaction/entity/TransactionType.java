package com.assetdashboard.domain.transaction.entity;

/** Asset 에 발생한 변경 이벤트의 종류. */
public enum TransactionType {

  /** 현금성 자산 입금. */
  DEPOSIT,

  /** 현금성 자산 출금. */
  WITHDRAW,

  /** 투자 자산 매수. */
  BUY,

  /** 투자 자산 매도. */
  SELL
}
