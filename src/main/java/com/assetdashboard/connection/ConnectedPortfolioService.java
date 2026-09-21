package com.assetdashboard.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConnectedPortfolioService {
  private final AccountConnectionRepository repository;
  private final ConnectionLifecycleService lifecycle;
  private final ConnectionProperties properties;
  private final ObjectMapper mapper;
  private final Clock clock;

  public record ConnectionView(Long id, ConnectionProvider provider, AccountConnection.Status status,
      Instant lastSyncedAt, Instant lastAttemptAt, Instant nextSyncAt, String errorCode,
      boolean stale, List<ConnectedHolding> holdings) {}
  public record Allocation(String category, BigDecimal knownValueKRW, BigDecimal percentage) {}
  public record Overview(boolean enabled, List<ConnectionView> connections, BigDecimal totalValueKRW,
      BigDecimal knownValueKRW, int unvaluedHoldingCount, int pendingConnectionCount,
      boolean stale, List<Allocation> allocation) {}

  @Transactional(readOnly = true)
  public Overview overview(Long userId) {
    lifecycle.ensureUser(userId);
    List<ConnectionView> connections = repository.findAllByUserIdOrderById(userId).stream().map(this::view).toList();
    BigDecimal known = BigDecimal.ZERO;
    Map<String, BigDecimal> byType = new LinkedHashMap<>();
    for (String type : List.of("STOCK", "CRYPTO", "CASH")) byType.put(type, BigDecimal.ZERO);
    int missing = 0;
    int pending = 0;
    for (ConnectionView c : connections) {
      if (c.lastSyncedAt() == null) pending++;
      for (ConnectedHolding h : c.holdings()) {
        if (h.valuationKRW() == null) { missing++; continue; }
        known = known.add(h.valuationKRW());
        byType.merge(h.category(), h.valuationKRW(), BigDecimal::add);
      }
    }
    BigDecimal total = connections.isEmpty() || pending > 0 || missing > 0 ? null : known;
    List<Allocation> allocation = byType.entrySet().stream().map(e -> new Allocation(e.getKey(), e.getValue(),
        total == null || total.signum() == 0 ? null : e.getValue().multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP))).toList();
    return new Overview(properties.enabled(), connections, total, known, missing, pending,
        connections.stream().anyMatch(ConnectionView::stale), allocation);
  }

  private ConnectionView view(AccountConnection c) {
    List<ConnectedHolding> holdings = List.of();
    if (c.getSnapshotJson() != null) {
      try { holdings = mapper.readValue(c.getSnapshotJson(), ConnectionSnapshot.class).holdings(); }
      catch (Exception e) { throw new IllegalStateException("Could not read connection snapshot"); }
    }
    boolean stale = !properties.enabled() || c.getLastSyncedAt() == null || c.getStatus() == AccountConnection.Status.ERROR
        || c.getLastSyncedAt().isBefore(clock.instant().minusSeconds(properties.syncIntervalSeconds() * 2L))
        || holdings.stream().anyMatch(h -> h.fxStale() ||
            (!"KRW".equals(h.currency()) && h.exchangeRateAt() != null &&
                h.exchangeRateAt().isBefore(LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()).minusMinutes(15))));
    return new ConnectionView(c.getId(), c.getProvider(), c.getStatus(), c.getLastSyncedAt(),
        c.getLastAttemptAt(), c.getNextSyncAt(), c.getErrorCode(), stale, holdings);
  }
}
