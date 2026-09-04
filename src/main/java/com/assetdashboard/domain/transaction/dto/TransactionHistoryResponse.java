package com.assetdashboard.domain.transaction.dto;

import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 거래 내역 목록의 한 줄 (PRD 4-3).
 *
 * @param id 거래 id
 * @param type 거래 종류
 * @param quantity 수량
 * @param price 단가 (입출금은 null)
 * @param exchangeRate 거래 시점 환율 (입출금은 null)
 * @param settlementAssetId 매매 대금을 주고받은 투자 대기자금 id
 * @param settlementAmount 정산된 원래 통화 금액
 * @param memo 메모
 * @param tradedAt 거래 시점
 */
public record TransactionHistoryResponse(
    Long id,
    TransactionType type,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal exchangeRate,
    Long settlementAssetId,
    BigDecimal settlementAmount,
    String memo,
    LocalDateTime tradedAt) {

  /**
   * 엔티티를 응답 DTO 로 변환한다.
   *
   * @param tx 변환할 거래
   * @return 거래 내역 응답
   */
  public static TransactionHistoryResponse from(Transaction tx) {
    return new TransactionHistoryResponse(
        tx.getId(),
        tx.getType(),
        tx.getQuantity(),
        tx.getPrice(),
        tx.getExchangeRate(),
        tx.getSettlementAssetId(),
        tx.getSettlementAmount(),
        tx.getMemo(),
        tx.getTradedAt());
  }
}
