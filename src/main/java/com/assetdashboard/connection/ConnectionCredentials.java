package com.assetdashboard.connection;

/** Never returned by an API or written to logs. */
public record ConnectionCredentials(String key, String secret) {
  @Override public String toString() { return "ConnectionCredentials[REDACTED]"; }
}
