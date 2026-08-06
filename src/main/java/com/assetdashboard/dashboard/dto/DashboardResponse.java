package com.assetdashboard.dashboard.dto;

import com.assetdashboard.domain.asset.dto.AllocationResponse;
import com.assetdashboard.domain.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dashboard 응답 (PRD 4-4).
 *
 * <p>Dashboard 는 Entity 가 없다. Asset 을 여러 관점에서 본 조회 결과를 모아 붙인 것이며, 그 조립을
 * {@code DashboardFacade} 가 담당한다.
 *
 * @param totalAssetKRW 총 자산 (KRW 환산). 삭제되지 않은 자산의 평가금액 합계
 * @param investmentSummary 투자 자산 요약
 * @param cashSummary 현금성 자산 요약
 * @param allocation type 단위 자산 배분 (Pie Chart 용)
 * @param recentTransactions 최근 거래 (최대 5건)
 * @param hasAssets 자산이 하나도 없으면 false — 프론트가 Empty State 를 보여준다
 * @param priceStale 시세가 오래된 자산이 하나라도 있으면 true
 */
public record DashboardResponse(
    BigDecimal totalAssetKRW,
    InvestmentSummary investmentSummary,
    CashSummary cashSummary,
    List<AllocationResponse> allocation,
    List<RecentTransaction> recentTransactions,
    boolean hasAssets,
    boolean priceStale) {

  /**
   * 투자 자산(STOCK + CRYPTO) 요약.
   *
   * <p>평가손익과 실현손익을 <b>합치지 않고 분리해서</b> 노출한다. 둘은 성격이 다른 숫자다 — 하나는 아직 확정되지
   * 않은 장부상 손익이고 다른 하나는 이미 확정된 손익이다(PRD Glossary).
   *
   * @param valuationKRW 평가금액 합계
   * @param costKRW 매입금액 합계
   * @param unrealizedPnl 평가손익 합계
   * @param unrealizedPnlRate 평가손익률(%). 매입금액이 0이면 null
   * @param realizedPnl 누적 실현손익 합계 (<b>삭제된 자산 포함</b>)
   */
  public record InvestmentSummary(
      BigDecimal valuationKRW,
      BigDecimal costKRW,
      BigDecimal unrealizedPnl,
      BigDecimal unrealizedPnlRate,
      BigDecimal realizedPnl) {}

  /**
   * 현금성 자산(CASH + BANK) 요약.
   *
   * @param valuationKRW 평가금액 합계
   */
  public record CashSummary(BigDecimal valuationKRW) {}

  /**
   * 최근 거래 한 줄.
   *
   * @param transactionId 거래 id
   * @param assetId 자산 id (탭하면 해당 자산의 거래 내역으로 이동)
   * @param assetName 자산 표시 이름
   * @param symbol 심볼
   * @param type 거래 종류
   * @param quantity 수량
   * @param price 단가 (입출금은 null)
   * @param tradedAt 거래 시점
   */
  public record RecentTransaction(
      Long transactionId,
      Long assetId,
      String assetName,
      String symbol,
      TransactionType type,
      BigDecimal quantity,
      BigDecimal price,
      LocalDateTime tradedAt) {}
}
