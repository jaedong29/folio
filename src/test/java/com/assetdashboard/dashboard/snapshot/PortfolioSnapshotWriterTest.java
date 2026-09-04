package com.assetdashboard.dashboard.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PortfolioSnapshotWriterTest {

  @Test
  void seedsPastDemoHistoryWithoutTouchingToday() {
    PortfolioSnapshotRepository repository = mock(PortfolioSnapshotRepository.class);
    when(repository.findAllByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            any(), any()))
        .thenReturn(List.of());
    PortfolioSnapshotWriter writer = new PortfolioSnapshotWriter(repository);
    LocalDate today = LocalDate.of(2026, 8, 9);

    int created = writer.seedDemoHistory(1L, new BigDecimal("100000000"), today);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<PortfolioSnapshot>> captor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(repository).saveAll(captor.capture());
    List<PortfolioSnapshot> snapshots =
        ((List<PortfolioSnapshot>) captor.getValue());
    assertThat(created).isEqualTo(89);
    assertThat(snapshots).hasSize(89).allMatch(PortfolioSnapshot::isDemoData);
    assertThat(snapshots.get(0).getSnapshotDate()).isEqualTo(today.minusDays(89));
    assertThat(snapshots.get(88).getSnapshotDate()).isEqualTo(today.minusDays(1));
    assertThat(snapshots).allMatch(snapshot -> snapshot.getTotalValueKrw().signum() > 0);
  }
}
