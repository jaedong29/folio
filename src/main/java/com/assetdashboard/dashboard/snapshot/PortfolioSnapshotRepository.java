package com.assetdashboard.dashboard.snapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Portfolio Snapshot 영속성 접근. */
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {

  /** 계정 탈퇴 시 사용자의 Snapshot 이력을 모두 삭제한다.
   *
   * @param userId 탈퇴할 사용자 id
   */
  void deleteAllByUserId(Long userId);

  /**
   * 사용자의 하루 기준 Snapshot을 조회한다.
   *
   * @param userId 사용자 id
   * @param snapshotDate 09:00 KST 경계 기준 날짜
   * @return Snapshot이 있으면 해당 값
   */
  Optional<PortfolioSnapshot> findByUserIdAndSnapshotDate(Long userId, LocalDate snapshotDate);

  /**
   * 분석 차트에 사용할 사용자의 기간별 Snapshot을 오래된 순서로 조회한다.
   *
   * @param userId 사용자 id
   * @param fromDate 조회 시작일 (포함)
   * @return 날짜 오름차순 Snapshot 목록
   */
  List<PortfolioSnapshot> findAllByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
      Long userId, LocalDate fromDate);
}
