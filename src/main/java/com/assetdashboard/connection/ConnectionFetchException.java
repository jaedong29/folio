package com.assetdashboard.connection;

/** Only controlled codes cross the provider boundary; signed URLs/bodies must not reach logs. */
public class ConnectionFetchException extends RuntimeException {
  public ConnectionFetchException(String code) { super(code); }
}
