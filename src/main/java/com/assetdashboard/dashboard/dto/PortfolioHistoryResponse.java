package com.assetdashboard.dashboard.dto;

import com.assetdashboard.dashboard.snapshot.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Asset Analysis의 순자산 추이 차트 응답.
 *
 * <p>Snapshot 값은 입출금을 포함한 당시의 실제 Portfolio 총액이다. 따라서 이 응답은 투자 수익률이 아니라
 * 순자산 추이를 표현하는 데만 사용한다.
 *
 * @param requestedDays 요청한 조회 기간
 * @param fromDate 조회 시작일
 * @param toDate 조회 종료일
 * @param demoData local 시연용 Snapshot이 하나라도 포함되면 true
 * @param points 날짜별 Portfolio 기준값
 */
public record PortfolioHistoryResponse(
    int requestedDays,
    LocalDate fromDate,
    LocalDate toDate,
    boolean demoData,
    List<Point> points) {

  /**
   * Portfolio Snapshot 한 점.
   *
   * @param date 09:00 KST 경계 기준 날짜
   * @param valueKRW Snapshot 당시 총 자산
   * @param capturedAt 실제 기준값 확보 시각
   */
  public record Point(LocalDate date, BigDecimal valueKRW, LocalDateTime capturedAt) {

    /**
     * Snapshot Entity를 응답 점으로 변환한다.
     *
     * @param snapshot 변환할 Snapshot
     * @return 차트 응답 점
     */
    public static Point from(PortfolioSnapshot snapshot) {
      return new Point(
          snapshot.getSnapshotDate(), snapshot.getTotalValueKrw(), snapshot.getCapturedAt());
    }
  }
}
