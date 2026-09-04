package com.assetdashboard.dashboard.snapshot;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.entity.TransactionType;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Snapshot과 외부 입출금만으로 단순한 Portfolio Daily PnL을 계산한다. */
@Service
@RequiredArgsConstructor
public class DailyPnlService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final LocalTime BASELINE_BOUNDARY = LocalTime.of(9, 0);
  private static final int MONEY_SCALE = 2;
  private static final int RATE_SCALE = 2;

  private final PortfolioSnapshotRepository snapshotRepository;
  private final PortfolioSnapshotWriter snapshotWriter;
  private final TransactionRepository transactionRepository;

  private final Map<Long, Object> creationLocks = new ConcurrentHashMap<>();

  /**
   * 현재 Portfolio 가치와 오늘 기준 Snapshot을 비교해 Daily PnL을 계산한다.
   *
   * <p>BUY/SELL은 Portfolio 내부 이동이라 보정하지 않는다. DEPOSIT/WITHDRAW만 외부 자금 흐름으로 보정하며,
   * Snapshot 이후 추가 등록한 초기 보유상태는 당일 수익으로 오인하지 않도록 외부 유입과 같은 방식으로 제외한다.
   *
   * @param userId 사용자 id
   * @param currentValueKrw 현재 총 투자자산
   * @param assets 현재 활성 자산
   * @param valuationComplete 모든 자산의 현재 KRW 평가가 가능한지 여부
   * @return 오늘 손익 계산 결과
   */
  public DailyPnlResult calculate(
      Long userId,
      BigDecimal currentValueKrw,
      List<Asset> assets,
      boolean valuationComplete) {
    boolean hasPortfolioContent =
        assets.stream()
            .anyMatch(
                asset ->
                    !asset.isDefaultSettlementAsset()
                        || asset.getQuantity().compareTo(BigDecimal.ZERO) > 0);
    if (!hasPortfolioContent) {
      return DailyPnlResult.unavailable("자산을 등록하면 오늘 손익 기준이 시작됩니다.");
    }
    if (!valuationComplete) {
      return DailyPnlResult.unavailable("시세 또는 환율이 없는 자산이 있어 오늘 손익을 계산할 수 없습니다.");
    }

    LocalDateTime now = LocalDateTime.now(KST);
    LocalDate effectiveDate = effectiveSnapshotDate(now);
    PortfolioSnapshot snapshot = getOrCreate(userId, effectiveDate, currentValueKrw, now);

    boolean quantityCorrectedAfterBaseline =
        assets.stream()
            .anyMatch(
                asset ->
                    asset.getPositionCorrectedAt() != null
                        && asset.getPositionCorrectedAt().isAfter(snapshot.getCapturedAt()));
    if (quantityCorrectedAfterBaseline) {
      return DailyPnlResult.unavailable(
          "오늘 보유 수량 정정이 있어 시장 손익과 데이터 수정을 정확히 분리할 수 없습니다. 다음 기준 시점부터 다시 계산됩니다.");
    }

    Map<Long, Asset> assetById = new HashMap<>();
    assets.forEach(asset -> assetById.put(asset.getId(), asset));

    BigDecimal netExternalFlow =
        calculateExternalFlow(assetById, snapshot.getCapturedAt(), now);
    if (netExternalFlow == null) {
      return DailyPnlResult.unavailable("외화 입출금의 거래 시점 환율이 없어 오늘 손익을 계산할 수 없습니다.");
    }
    netExternalFlow =
        netExternalFlow.add(calculateOpeningPositionFlow(assets, snapshot.getCapturedAt()));

    BigDecimal amount =
        currentValueKrw
            .subtract(snapshot.getTotalValueKrw())
            .subtract(netExternalFlow)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal rate =
        snapshot.getTotalValueKrw().compareTo(BigDecimal.ZERO) == 0
            ? null
            : amount
                .multiply(BigDecimal.valueOf(100))
                .divide(snapshot.getTotalValueKrw(), RATE_SCALE, RoundingMode.HALF_UP);

    return new DailyPnlResult(
        amount,
        rate,
        snapshot.getTotalValueKrw(),
        netExternalFlow.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
        snapshot.getCapturedAt(),
        true,
        null);
  }

  private PortfolioSnapshot getOrCreate(
      Long userId,
      LocalDate effectiveDate,
      BigDecimal currentValueKrw,
      LocalDateTime capturedAt) {
    return snapshotRepository
        .findByUserIdAndSnapshotDate(userId, effectiveDate)
        .orElseGet(
            () -> {
              Object lock = creationLocks.computeIfAbsent(userId, key -> new Object());
              synchronized (lock) {
                try {
                  return snapshotWriter.createIfAbsent(
                      userId, effectiveDate, currentValueKrw, capturedAt);
                } finally {
                  creationLocks.remove(userId, lock);
                }
              }
            });
  }

  private LocalDate effectiveSnapshotDate(LocalDateTime now) {
    return now.toLocalTime().isBefore(BASELINE_BOUNDARY)
        ? now.toLocalDate().minusDays(1)
        : now.toLocalDate();
  }

  private BigDecimal calculateExternalFlow(
      Map<Long, Asset> assetById, LocalDateTime from, LocalDateTime to) {
    if (assetById.isEmpty()) {
      return BigDecimal.ZERO;
    }
    List<Transaction> flows =
        transactionRepository.findExternalFlows(List.copyOf(assetById.keySet()), from, to);

    BigDecimal total = BigDecimal.ZERO;
    for (Transaction tx : flows) {
      Asset asset = assetById.get(tx.getAssetId());
      BigDecimal rate = tx.getExchangeRate();
      if (rate == null && asset != null && "KRW".equalsIgnoreCase(asset.getCurrency())) {
        rate = BigDecimal.ONE;
      }
      if (rate == null) {
        return null;
      }
      BigDecimal value = tx.getQuantity().multiply(rate);
      total =
          tx.getType() == TransactionType.DEPOSIT ? total.add(value) : total.subtract(value);
    }
    return total;
  }

  private BigDecimal calculateOpeningPositionFlow(
      List<Asset> assets, LocalDateTime baselineAt) {
    return assets.stream()
        .filter(asset -> asset.getCreatedAt() != null && asset.getCreatedAt().isAfter(baselineAt))
        .filter(
            asset ->
                asset.getInitialQuantity() != null
                    && asset.getInitialQuantity().compareTo(BigDecimal.ZERO) > 0)
        // 등록 당일에는 새로 편입한 Position 전체를 외부 유입으로 보아 가짜 수익을 만들지 않는다.
        .map(
            asset ->
                asset.getInitialQuantity()
                    .multiply(asset.getCurrentPrice())
                    .multiply(asset.getCurrentExchangeRate()))
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
