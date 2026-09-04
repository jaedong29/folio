package com.assetdashboard.news;

/** 외부 응답 원문을 노출하지 않고 안전한 코드만 작업 결과에 남긴다. */
public class NewsSourceException extends RuntimeException {

  private final String safeCode;

  public NewsSourceException(String safeCode) {
    super(safeCode);
    this.safeCode = safeCode;
  }

  public NewsSourceException(String safeCode, Throwable cause) {
    super(safeCode, cause);
    this.safeCode = safeCode;
  }

  public String safeCode() {
    return safeCode;
  }
}
