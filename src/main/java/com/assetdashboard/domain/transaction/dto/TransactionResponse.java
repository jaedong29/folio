package com.assetdashboard.domain.transaction.dto;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 거래 생성 응답 (PRD 4-3).
 *
 * <p>거래 결과로 <b>자산이 어떻게 바뀌었는지</b>를 함께 돌려준다. 클라이언트가 거래 후 자산을 다시 조회하지 않아도
 * 화면을 갱신할 수 있고, 평단가·실현손익 계산이 맞는지 즉시 확인할 수 있다.
 *
 * @param transactionId 생성된 거래 id
 * @param type 거래 종류
 * @param quantity 거래 수량
 * @param price 거래 단가 (입출금은 null)
 * @param exchangeRate 거래 시점 환율 (입출금은 null)
 * @param memo 메모
 * @param tradedAt 거래 시점
 * @param asset 거래 반영 후의 자산 상태
 */
public record TransactionResponse(
    Long transactionId,
    TransactionType type,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal exchangeRate,
    String memo,
    LocalDateTime tradedAt,
    AssetSnapshot asset) {

  /**
   * 거래 반영 직후의 자산 상태 요약.
   *
   * @param id 자산 id
   * @param symbol 심볼
   * @param name 표시 이름
   * @param quantity 반영 후 보유 수량
   * @param avgPrice 반영 후 평균 매입 단가 (KRW)
   * @param realizedPnl 반영 후 누적 실현손익 (KRW)
   */
  public record AssetSnapshot(
      Long id,
      String symbol,
      String name,
      BigDecimal quantity,
      BigDecimal avgPrice,
      BigDecimal realizedPnl) {}

  /**
   * 거래와 자산 상태를 응답 DTO 로 변환한다.
   *
   * @param tx 생성된 거래
   * @param asset 거래가 반영된 자산
   * @return 거래 응답
   */
  public static TransactionResponse from(Transaction tx, Asset asset) {
    return new TransactionResponse(
        tx.getId(),
        tx.getType(),
        tx.getQuantity(),
        tx.getPrice(),
        tx.getExchangeRate(),
        tx.getMemo(),
        tx.getTradedAt(),
        new AssetSnapshot(
            asset.getId(),
            asset.getSymbol(),
            asset.getName(),
            asset.getQuantity(),
            asset.getAvgPrice(),
            asset.getRealizedPnl()));
  }
}
