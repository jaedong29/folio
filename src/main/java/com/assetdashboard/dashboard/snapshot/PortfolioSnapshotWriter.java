package com.assetdashboard.dashboard.snapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Snapshot 최초 생성을 짧은 독립 트랜잭션으로 처리한다. */
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotWriter {

  private final PortfolioSnapshotRepository snapshotRepository;

  /**
   * 해당 날짜 Snapshot이 없을 때만 생성한다.
   *
   * @param userId 사용자 id
   * @param snapshotDate 09:00 KST 경계 기준 날짜
   * @param totalValueKrw 현재 총 투자자산
   * @param capturedAt 실제 기준 시각
   * @return 기존 또는 새 Snapshot
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public PortfolioSnapshot createIfAbsent(
      Long userId,
      LocalDate snapshotDate,
      BigDecimal totalValueKrw,
      LocalDateTime capturedAt) {
    return snapshotRepository
        .findByUserIdAndSnapshotDate(userId, snapshotDate)
        .orElseGet(
            () ->
                snapshotRepository.save(
                    PortfolioSnapshot.create(
                        userId, snapshotDate, totalValueKrw, capturedAt)));
  }

  /**
   * local 발표 계정에만 사용할 90일 설명용 순자산 이력을 채운다.
   *
   * <p>오늘 값은 실제 Daily PnL이 관리하므로 건드리지 않고, 이미 존재하는 날짜도 덮어쓰지 않는다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int seedDemoHistory(Long userId, BigDecimal currentValueKrw, LocalDate today) {
    LocalDate fromDate = today.minusDays(89);
    Set<LocalDate> existingDates =
        new HashSet<>(
            snapshotRepository
                .findAllByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(userId, fromDate)
                .stream()
                .map(PortfolioSnapshot::getSnapshotDate)
                .toList());

    List<PortfolioSnapshot> snapshots = new ArrayList<>();
    for (int daysAgo = 89; daysAgo >= 1; daysAgo--) {
      LocalDate date = today.minusDays(daysAgo);
      if (existingDates.contains(date)) {
        continue;
      }
      int index = 89 - daysAgo;
      double progress = (index + 1) / 89.0;
      double wave = Math.sin(index * 0.47) * 0.010 + Math.cos(index * 0.19) * 0.006;
      double factor = 0.885 + progress * 0.108 + wave;
      BigDecimal value =
          currentValueKrw
              .multiply(BigDecimal.valueOf(factor))
              .setScale(2, RoundingMode.HALF_UP);
      snapshots.add(
          PortfolioSnapshot.createDemo(userId, date, value, date.atTime(9, 0)));
    }
    snapshotRepository.saveAll(snapshots);
    return snapshots.size();
  }
}
