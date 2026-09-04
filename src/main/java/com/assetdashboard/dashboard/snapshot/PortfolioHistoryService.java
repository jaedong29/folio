package com.assetdashboard.dashboard.snapshot;

import com.assetdashboard.dashboard.dto.PortfolioHistoryResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Asset Analysis 화면에 Portfolio Snapshot 이력을 제공한다. */
@Service
@RequiredArgsConstructor
public class PortfolioHistoryService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int MIN_DAYS = 1;
  private static final int MAX_DAYS = 365;

  private final PortfolioSnapshotRepository snapshotRepository;

  /**
   * 요청 기간 안의 Snapshot을 오래된 순서로 조회한다.
   *
   * <p>과도한 전체 조회를 피하기 위해 기간은 1일에서 365일 사이로 제한한다.
   *
   * @param userId 사용자 id
   * @param days 조회할 일수
   * @return 순자산 추이 응답
   * @throws IllegalArgumentException 기간이 허용 범위를 벗어난 경우
   */
  @Transactional(readOnly = true)
  public PortfolioHistoryResponse getHistory(Long userId, int days) {
    if (days < MIN_DAYS || days > MAX_DAYS) {
      throw new IllegalArgumentException("조회 기간은 1일에서 365일 사이여야 합니다.");
    }

    LocalDate toDate = LocalDate.now(KST);
    LocalDate fromDate = toDate.minusDays(days - 1L);
    List<PortfolioSnapshot> snapshots =
        snapshotRepository
            .findAllByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(userId, fromDate);
    List<PortfolioHistoryResponse.Point> points =
        snapshots.stream().map(PortfolioHistoryResponse.Point::from).toList();
    boolean demoData = snapshots.stream().anyMatch(PortfolioSnapshot::isDemoData);
    return new PortfolioHistoryResponse(days, fromDate, toDate, demoData, points);
  }
}
