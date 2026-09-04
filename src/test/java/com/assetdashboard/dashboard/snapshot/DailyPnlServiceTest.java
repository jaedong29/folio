package com.assetdashboard.dashboard.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailyPnlServiceTest {

  @Test
  void subtractsExternalDepositButNotMarketGain() {
    PortfolioSnapshotRepository snapshotRepository = mock(PortfolioSnapshotRepository.class);
    PortfolioSnapshotWriter snapshotWriter = mock(PortfolioSnapshotWriter.class);
    TransactionRepository transactionRepository = mock(TransactionRepository.class);
    DailyPnlService service =
        new DailyPnlService(snapshotRepository, snapshotWriter, transactionRepository);

    LocalDateTime baselineAt = LocalDateTime.now().minusHours(1);
    PortfolioSnapshot snapshot =
        PortfolioSnapshot.create(1L, LocalDate.now(), new BigDecimal("10000000"), baselineAt);
    when(snapshotRepository.findByUserIdAndSnapshotDate(eq(1L), any(LocalDate.class)))
        .thenReturn(Optional.of(snapshot));

    Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(10L);
    when(asset.getCreatedAt()).thenReturn(baselineAt.minusDays(1));
    Transaction deposit =
        Transaction.createDeposit(
            10L,
            new BigDecimal("1000000"),
            BigDecimal.ONE,
            "외부 입금",
            baselineAt.plusMinutes(10));
    when(transactionRepository.findExternalFlows(anyList(), any(), any()))
        .thenReturn(List.of(deposit));

    DailyPnlResult result =
        service.calculate(1L, new BigDecimal("12000000"), List.of(asset), true);

    assertThat(result.available()).isTrue();
    assertThat(result.netExternalFlowKrw()).isEqualByComparingTo("1000000.00");
    assertThat(result.amountKrw()).isEqualByComparingTo("1000000.00");
    assertThat(result.rate()).isEqualByComparingTo("10.00");
  }

  @Test
  void doesNotCreateBaselineWhenAnyValuationIsMissing() {
    PortfolioSnapshotRepository snapshotRepository = mock(PortfolioSnapshotRepository.class);
    PortfolioSnapshotWriter snapshotWriter = mock(PortfolioSnapshotWriter.class);
    TransactionRepository transactionRepository = mock(TransactionRepository.class);
    DailyPnlService service =
        new DailyPnlService(snapshotRepository, snapshotWriter, transactionRepository);

    DailyPnlResult result =
        service.calculate(1L, BigDecimal.ZERO, List.of(mock(Asset.class)), false);

    assertThat(result.available()).isFalse();
    assertThat(result.unavailableReason()).contains("시세 또는 환율");
    verifyNoInteractions(snapshotRepository, snapshotWriter, transactionRepository);
  }

  @Test
  void zeroDefaultCashDoesNotStartDailyBaseline() {
    PortfolioSnapshotRepository snapshotRepository = mock(PortfolioSnapshotRepository.class);
    PortfolioSnapshotWriter snapshotWriter = mock(PortfolioSnapshotWriter.class);
    TransactionRepository transactionRepository = mock(TransactionRepository.class);
    DailyPnlService service =
        new DailyPnlService(snapshotRepository, snapshotWriter, transactionRepository);

    List<Asset> defaults =
        List.of(
            Asset.create(1L, AssetType.CASH, "KRW", "원화 대기자금", "KRW"),
            Asset.create(1L, AssetType.CASH, "USD", "달러 대기자금", "USD"),
            Asset.create(1L, AssetType.CASH, "USDT", "테더 대기자금", "USDT"));

    DailyPnlResult result = service.calculate(1L, BigDecimal.ZERO, defaults, true);

    assertThat(result.available()).isFalse();
    assertThat(result.unavailableReason()).contains("자산을 등록하면");
    verifyNoInteractions(snapshotRepository, snapshotWriter, transactionRepository);
  }

  @Test
  void quantityCorrectionAfterBaselineDoesNotAppearAsDailyMarketLoss() {
    PortfolioSnapshotRepository snapshotRepository = mock(PortfolioSnapshotRepository.class);
    PortfolioSnapshotWriter snapshotWriter = mock(PortfolioSnapshotWriter.class);
    TransactionRepository transactionRepository = mock(TransactionRepository.class);
    DailyPnlService service =
        new DailyPnlService(snapshotRepository, snapshotWriter, transactionRepository);

    LocalDateTime baselineAt = LocalDateTime.now().minusHours(1);
    PortfolioSnapshot snapshot =
        PortfolioSnapshot.create(1L, LocalDate.now(), new BigDecimal("10000000"), baselineAt);
    when(snapshotRepository.findByUserIdAndSnapshotDate(eq(1L), any(LocalDate.class)))
        .thenReturn(Optional.of(snapshot));
    Asset asset = mock(Asset.class);
    when(asset.getPositionCorrectedAt()).thenReturn(baselineAt.plusMinutes(10));

    DailyPnlResult result =
        service.calculate(1L, new BigDecimal("9000000"), List.of(asset), true);

    assertThat(result.available()).isFalse();
    assertThat(result.unavailableReason()).contains("보유 수량 정정", "다음 기준 시점");
    verifyNoInteractions(transactionRepository);
  }
}
