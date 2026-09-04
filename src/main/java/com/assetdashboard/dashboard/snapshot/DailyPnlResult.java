package com.assetdashboard.dashboard.snapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dashboard에 전달할 오늘 Portfolio 손익 계산 결과.
 *
 * @param amountKrw 오늘 손익 금액
 * @param rate 오늘 손익률
 * @param baselineValueKrw 기준 Portfolio 가치
 * @param netExternalFlowKrw 기준 이후 순외부입금액
 * @param baselineAt 실제 기준 시각
 * @param available 계산 가능 여부
 * @param unavailableReason 계산할 수 없을 때 사용자에게 보여줄 이유
 */
public record DailyPnlResult(
    BigDecimal amountKrw,
    BigDecimal rate,
    BigDecimal baselineValueKrw,
    BigDecimal netExternalFlowKrw,
    LocalDateTime baselineAt,
    boolean available,
    String unavailableReason) {

  /**
   * 현재 데이터로 오늘 손익을 계산할 수 없는 결과를 만든다.
   *
   * @param reason 계산 불가 이유
   * @return unavailable 결과
   */
  public static DailyPnlResult unavailable(String reason) {
    return new DailyPnlResult(null, null, null, null, null, false, reason);
  }
}
