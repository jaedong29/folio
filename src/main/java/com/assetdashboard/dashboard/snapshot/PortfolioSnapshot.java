package com.assetdashboard.dashboard.snapshot;

import com.assetdashboard.global.common.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 하루 Portfolio Daily PnL 계산의 실제 기준점. */
@Getter
@Entity
@Table(
    name = "portfolio_snapshots",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_snapshot_user_date",
            columnNames = {"user_id", "snapshot_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioSnapshot extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  /** 09:00 KST 경계를 기준으로 구분한 Portfolio 날짜. */
  @Column(name = "snapshot_date", nullable = false)
  private LocalDate snapshotDate;

  /** Snapshot 생성 당시 Portfolio 전체 KRW 평가액. */
  @Column(name = "total_value_krw", nullable = false, precision = 20, scale = 2)
  private BigDecimal totalValueKrw;

  /** 실제 기준값을 확보한 시각. 서버 중단 등으로 늦어져도 이 시각을 그대로 화면에 표시한다. */
  @Column(name = "captured_at", nullable = false)
  private LocalDateTime capturedAt;

  /** local 발표 시드로 만든 설명용 이력이면 true. 실제 Snapshot과 화면에서 명확히 구분한다. */
  @Column(name = "demo_data", nullable = false, columnDefinition = "boolean default false")
  private boolean demoData;

  private PortfolioSnapshot(
      Long userId,
      LocalDate snapshotDate,
      BigDecimal totalValueKrw,
      LocalDateTime capturedAt,
      boolean demoData) {
    this.userId = userId;
    this.snapshotDate = snapshotDate;
    this.totalValueKrw = totalValueKrw;
    this.capturedAt = capturedAt;
    this.demoData = demoData;
  }

  /**
   * 실제로 확보한 현재 Portfolio 값을 기준점으로 저장할 Snapshot을 생성한다.
   *
   * @param userId 사용자 id
   * @param snapshotDate 09:00 KST 경계 기준 날짜
   * @param totalValueKrw 현재 총 투자자산
   * @param capturedAt 실제 확보 시각
   * @return 새 Snapshot
   */
  public static PortfolioSnapshot create(
      Long userId,
      LocalDate snapshotDate,
      BigDecimal totalValueKrw,
      LocalDateTime capturedAt) {
    return new PortfolioSnapshot(userId, snapshotDate, totalValueKrw, capturedAt, false);
  }

  /** local 시연에서만 사용하는 설명용 과거 Snapshot을 생성한다. */
  public static PortfolioSnapshot createDemo(
      Long userId,
      LocalDate snapshotDate,
      BigDecimal totalValueKrw,
      LocalDateTime capturedAt) {
    return new PortfolioSnapshot(userId, snapshotDate, totalValueKrw, capturedAt, true);
  }
}
