package com.assetdashboard.global.exception;

/**
 * 공통 에러 응답 본문 (PRD 4-7).
 *
 * @param status HTTP 상태 코드
 * @param code 에러 코드 이름
 * @param message 사용자에게 보여줄 메시지
 */
public record ErrorResponse(int status, String code, String message) {

  /**
   * 에러 코드의 기본 메시지로 응답을 만든다.
   *
   * @param errorCode 에러 코드
   * @return 에러 응답
   */
  public static ErrorResponse of(ErrorCode errorCode) {
    return of(errorCode, errorCode.getMessage());
  }

  /**
   * 에러 코드와 상세 메시지로 응답을 만든다.
   *
   * @param errorCode 에러 코드
   * @param message 상세 메시지
   * @return 에러 응답
   */
  public static ErrorResponse of(ErrorCode errorCode, String message) {
    return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), message);
  }
}
