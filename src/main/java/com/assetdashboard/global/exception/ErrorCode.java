package com.assetdashboard.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API 공통 에러 코드.
 *
 * <p>PRD 4-7의 에러 표를 그대로 옮긴 것이며, 회원가입·로그인처럼 PRD가 코드를 명시하지 않은 인증 실패 케이스만
 * {@link #DUPLICATE_EMAIL}, {@link #INVALID_CREDENTIALS}로 보강했다.
 */
@Getter
public enum ErrorCode {

  /** 매도·출금 수량이 보유 수량을 초과한 경우. */
  INSUFFICIENT_ASSET_QUANTITY(HttpStatus.BAD_REQUEST, "보유 수량이 부족합니다."),

  /** 필수 필드 누락, 값 범위 위반 등 요청 자체가 잘못된 경우. */
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

  /** Asset 등록 시 외부 시세 API에서 조회되지 않는 심볼인 경우. */
  INVALID_SYMBOL(
      HttpStatus.BAD_REQUEST, "해당 심볼의 시세를 찾을 수 없습니다. 국내주식은 000660.KS 형식으로 입력해주세요."),

  /** 토큰이 없거나 만료·위조된 경우. */
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

  /** 이메일 또는 비밀번호가 일치하지 않는 경우. */
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

  /**
   * 타인 소유 자산 접근 시도. 클라이언트에는 노출하지 않고 서버 로그 기록 전용으로만 사용한다 (PRD 4-0 규칙 3).
   */
  FORBIDDEN_ASSET_ACCESS(HttpStatus.FORBIDDEN, "해당 자산에 접근할 권한이 없습니다."),

  /** 존재하지 않는 Asset id 또는 타인 소유 Asset. */
  ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "자산을 찾을 수 없습니다."),

  /** 존재하지 않는 거래 id 또는 다른 자산에 속한 거래. */
  TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "거래를 찾을 수 없습니다."),

  /**
   * 거래를 삭제하면 이후 거래의 보유 수량이 음수가 되는 경우.
   *
   * <p>예: 매수 10 → 매도 5 상태에서 매수를 지우면 매도할 수량이 없어진다. 데이터를 망가뜨리는 대신 삭제를
   * 거부하고, 나중 거래부터 지우도록 안내한다.
   */
  TRANSACTION_DELETE_BREAKS_HISTORY(
      HttpStatus.BAD_REQUEST, "이 거래를 삭제하면 이후 거래의 보유 수량이 음수가 됩니다. 최근 거래부터 순서대로 삭제해주세요."),

  /** 동일 (type, symbol) 자산을 중복 등록한 경우. */
  DUPLICATE_ASSET(HttpStatus.CONFLICT, "이미 등록된 자산입니다."),

  /** 이미 가입된 이메일로 회원가입을 시도한 경우. */
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

  /** 동시 거래로 낙관적 락이 충돌한 경우. */
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "다른 요청이 먼저 처리되었습니다. 잠시 후 다시 시도해주세요."),

  /** 그 밖의 서버 내부 오류. */
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }
}
