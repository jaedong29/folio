package com.assetdashboard.connection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A provider-observed position, never replayed as a synthetic trade in the manual ledger. */
public record ConnectedHolding(
    String symbol, String name, String category, String currency,
    BigDecimal quantity, BigDecimal lockedQuantity, BigDecimal price,
    BigDecimal averagePurchasePrice, BigDecimal providerProfitLoss,
    BigDecimal valuation, BigDecimal exchangeRate, LocalDateTime exchangeRateAt,
    String exchangeRateSource, BigDecimal valuationKRW, boolean fxStale) {}
