package com.assetdashboard.infra.price;

import lombok.Getter;

/**
 * 외부 시세 조회에 실패했을 때 던진다.
 *
 * <p>이 예외는 <b>상위에서 반드시 잡혀 폴백 처리된다.</b> 시세 조회 실패가 대시보드 전체를 500 으로 만들면 안 되기
 * 때문이다.
 *
 * <p>{@link Kind} 로 실패 원인을 구분하는 이유는 자산 등록 시점의 처리가 정반대이기 때문이다. 존재하지 않는
 * 심볼이면 사용자 입력이 잘못된 것이므로 {@code 400 INVALID_SYMBOL} 로 막아야 하지만, 외부 API 장애라면
 * 등록 자체를 막아서는 안 된다 — 우리 쪽 문제로 사용자가 자산을 등록하지 못하게 되기 때문이다(PRD 4-2).
 */
@Getter
public class PriceProviderException extends RuntimeException {

  /** 시세 조회 실패의 원인 구분. */
  public enum Kind {
    /** 응답은 정상이었으나 해당 심볼의 가격이 없다. 사용자 입력 오류. */
    SYMBOL_NOT_FOUND,

    /** 타임아웃·네트워크 오류·5xx 등 외부 API 쪽 문제. */
    PROVIDER_UNAVAILABLE
  }

  private final Kind kind;

  /**
   * 원인 구분과 사유를 담아 예외를 생성한다.
   *
   * @param kind 실패 원인 구분
   * @param message 실패 사유
   */
  public PriceProviderException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  /**
   * 원인 구분·사유·원인 예외를 담아 예외를 생성한다.
   *
   * @param kind 실패 원인 구분
   * @param message 실패 사유
   * @param cause 원인 예외
   */
  public PriceProviderException(Kind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = kind;
  }

  /**
   * 존재하지 않는 심볼인지 여부.
   *
   * @return 사용자 입력이 잘못된 경우 {@code true}
   */
  public boolean isSymbolNotFound() {
    return kind == Kind.SYMBOL_NOT_FOUND;
  }
}
