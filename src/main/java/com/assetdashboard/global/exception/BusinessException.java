package com.assetdashboard.global.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 나타내는 최상위 예외.
 *
 * <p>{@link ErrorCode}를 그대로 들고 다니므로 {@link GlobalExceptionHandler}가 HTTP 상태와 응답 본문을 일관되게
 * 만들어낼 수 있다.
 */
@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  /**
   * 에러 코드의 기본 메시지로 예외를 생성한다.
   *
   * @param errorCode 위반된 규칙에 대응하는 에러 코드
   */
  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  /**
   * 에러 코드와 상황별 메시지로 예외를 생성한다.
   *
   * @param errorCode 위반된 규칙에 대응하는 에러 코드
   * @param message 기본 메시지를 대체할 상세 메시지
   */
  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
