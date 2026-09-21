package com.assetdashboard.connection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.connections")
public record ConnectionProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("") String encryptionKey,
    @DefaultValue("300") int syncIntervalSeconds) {
  public ConnectionProperties {
    if (syncIntervalSeconds < 60) throw new IllegalArgumentException("Connection sync interval must be >= 60 seconds");
  }
  @Override public String toString() { return "ConnectionProperties[enabled=" + enabled + "]"; }
}
