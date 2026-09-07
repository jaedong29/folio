package com.assetdashboard.global.resilience;

/** 연속 실패로 열린 Circuit Breaker가 외부 호출을 실행 전에 거부했음을 나타낸다. */
public class ExternalCallRejectedException extends RuntimeException {

  private final ExternalSource source;

  public ExternalCallRejectedException(ExternalSource source, Throwable cause) {
    super("외부 호출이 일시적으로 차단되었습니다: " + source, cause);
    this.source = source;
  }

  public ExternalSource source() {
    return source;
  }
}
