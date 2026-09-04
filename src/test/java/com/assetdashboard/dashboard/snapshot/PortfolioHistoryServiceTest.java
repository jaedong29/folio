package com.assetdashboard.dashboard.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetdashboard.dashboard.dto.PortfolioHistoryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioHistoryServiceTest {

  @Test
  void returnsSnapshotsInRepositoryOrder() {
    PortfolioSnapshotRepository repository = mock(PortfolioSnapshotRepository.class);
    PortfolioHistoryService service = new PortfolioHistoryService(repository);
    PortfolioSnapshot first =
        PortfolioSnapshot.create(
            1L, LocalDate.now().minusDays(1), new BigDecimal("1000000"), LocalDateTime.now());
    PortfolioSnapshot second =
        PortfolioSnapshot.createDemo(
            1L, LocalDate.now(), new BigDecimal("1100000"), LocalDateTime.now());
    when(repository.findAllByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            any(), any()))
        .thenReturn(List.of(first, second));

    PortfolioHistoryResponse response = service.getHistory(1L, 30);

    assertThat(response.requestedDays()).isEqualTo(30);
    assertThat(response.demoData()).isTrue();
    assertThat(response.points()).extracting(PortfolioHistoryResponse.Point::valueKRW)
        .containsExactly(new BigDecimal("1000000"), new BigDecimal("1100000"));
  }

  @Test
  void rejectsOutOfRangePeriod() {
    PortfolioHistoryService service =
        new PortfolioHistoryService(mock(PortfolioSnapshotRepository.class));

    assertThatThrownBy(() -> service.getHistory(1L, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1일에서 365일");
  }
}
