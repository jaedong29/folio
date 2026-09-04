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

  /** 매수 또는 거래 삭제 정산에 필요한 투자 대기자금의 잔액이 부족한 경우. */
  INSUFFICIENT_SETTLEMENT_FUNDS(HttpStatus.BAD_REQUEST, "정산할 투자 대기자금이 부족합니다."),

  /** 투자 자산과 선택한 정산 자산의 통화가 다른 경우. */
  SETTLEMENT_CURRENCY_MISMATCH(HttpStatus.BAD_REQUEST, "매매 자산과 정산 자산의 통화가 일치하지 않습니다."),

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

  /** 같은 자산에 같은 본문의 근거 자료를 다시 등록한 경우. */
  DUPLICATE_EVIDENCE_DOCUMENT(HttpStatus.CONFLICT, "이미 등록된 근거 자료입니다."),

  /** 존재하지 않거나 타인 소유인 근거 문서. */
  EVIDENCE_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "근거 자료를 찾을 수 없습니다."),

  /** 존재하지 않거나 타인 소유인 Agent Trace. */
  AGENT_TRACE_NOT_FOUND(HttpStatus.NOT_FOUND, "Agent 실행 기록을 찾을 수 없습니다."),

  /** 존재하지 않거나 타인 소유인 실제 NIM 평가 배치. */
  EVALUATION_BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "평가 배치를 찾을 수 없습니다."),

  /** 화이트리스트에 등록되지 않은 뉴스 수집 출처. */
  NEWS_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 뉴스 출처를 찾을 수 없습니다."),

  /** 존재하지 않는 뉴스 갱신 작업. */
  NEWS_REFRESH_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "뉴스 갱신 작업을 찾을 수 없습니다."),

  /** 외부 뉴스 수집을 명시적으로 비활성화한 환경. */
  NEWS_COLLECTION_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "외부 뉴스 수집이 비활성화되어 있습니다."),

  /** 명시적으로 켜지 않았거나 API 키가 없어 Agent를 실행할 수 없는 경우. */
  AI_AGENT_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "금융 AI Agent가 비활성화되어 있습니다."),

  /** 외부 LLM 제공자 연결·타임아웃·HTTP 오류. */
  AI_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 모델 제공자에 연결할 수 없습니다."),

  /** 제공자가 Tool 또는 최종 답변 계약과 다른 응답을 반환한 경우. */
  AI_PROVIDER_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "AI 모델 응답 형식이 올바르지 않습니다."),

  /** 오늘의 NIM 호출·토큰 예산을 초과해 추가 호출을 막은 경우. */
  AI_BUDGET_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘의 AI 사용 한도를 초과했습니다."),

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
