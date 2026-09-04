package com.assetdashboard.evidence.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하루 단위로 누적하는 NIM 호출 수·토큰 사용량.
 *
 * <p>Financial Evidence Agent와 News 요약이 이 카운터를 함께 사용해 예산 초과 여부를 판단한다. 질문·답변
 * 원문은 남기지 않고 집계만 유지한다.
 */
@Getter
@Entity
@Table(name = "llm_daily_usage")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmDailyUsage {

  @Id
  @Column(name = "usage_date", nullable = false)
  private LocalDate usageDate;

  @Column(name = "call_count", nullable = false)
  private long callCount;

  @Column(name = "input_tokens", nullable = false)
  private long inputTokens;

  @Column(name = "output_tokens", nullable = false)
  private long outputTokens;

  private LlmDailyUsage(LocalDate usageDate) {
    this.usageDate = usageDate;
  }

  /** 테스트가 특정 사용량 상태를 준비할 때만 쓴다. 운영 코드는 항상 원자적 UPDATE로만 값을 바꾼다. */
  LlmDailyUsage(LocalDate usageDate, long callCount, long inputTokens, long outputTokens) {
    this.usageDate = usageDate;
    this.callCount = callCount;
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
  }

  /** 아직 DB에 오늘 행이 없을 때 0으로 시작하는 값을 표현한다(저장 여부는 호출자가 결정). */
  public static LlmDailyUsage startingToday(LocalDate usageDate) {
    return new LlmDailyUsage(usageDate);
  }
}
