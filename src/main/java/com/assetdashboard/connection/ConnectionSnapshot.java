package com.assetdashboard.connection;

import java.time.Instant;
import java.util.List;

public record ConnectionSnapshot(Instant observedAt, List<ConnectedHolding> holdings) {
  public ConnectionSnapshot { holdings = List.copyOf(holdings); }
}
