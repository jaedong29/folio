package com.assetdashboard.global.exception;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 컨트롤러 예외를 PRD 4-7의 공통 에러 형식으로 변환하는 핸들러.
 *
 * <p>컨트롤러가 예외 처리 코드를 갖지 않도록 하고, 어떤 경로로 실패하든 응답 형식이 하나로 유지되게 한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * 비즈니스 규칙 위반을 해당 에러 코드의 상태로 응답한다.
   *
   * @param e 발생한 비즈니스 예외
   * @return 에러 응답
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
    ErrorCode code = e.getErrorCode();
    log.warn("[BusinessException] {} - {}", code.name(), e.getMessage());
    return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
  }

  /**
   * Bean Validation 위반을 {@code INVALID_INPUT}으로 응답한다.
   *
   * @param e 검증 실패 예외
   * @return 어떤 필드가 왜 실패했는지 담은 에러 응답
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return badRequest(detail.isBlank() ? ErrorCode.INVALID_INPUT.getMessage() : detail);
  }

  /**
   * 쿼리 파라미터·경로 변수 수준의 제약 위반을 {@code INVALID_INPUT}으로 응답한다.
   *
   * @param e 제약 위반 예외
   * @return 에러 응답
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
    return badRequest(e.getMessage());
  }

  /** 알 수 없는 JSON 필드는 오타 난 필드명과 사용 가능한 필드 목록을 함께 알려준다. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
    log.warn("[InvalidRequest] {}", e.getMessage());
    UnrecognizedPropertyException unknown = findCause(e, UnrecognizedPropertyException.class);
    if (unknown != null) {
      String available =
          unknown.getKnownPropertyIds().stream()
              .filter(Objects::nonNull)
              .map(Object::toString)
              .sorted(Comparator.naturalOrder())
              .collect(Collectors.joining(", "));
      String message = "알 수 없는 필드입니다: %s".formatted(unknown.getPropertyName());
      if (!available.isBlank()) {
        message += ". 사용 가능한 필드: " + available;
      }
      return badRequest(message);
    }
    return badRequest(ErrorCode.INVALID_INPUT.getMessage());
  }

  /** 쿼리 파라미터·경로 변수 파싱 실패를 공통 입력 오류로 응답한다. */
  @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
  public ResponseEntity<ErrorResponse> handleParameterMismatch(Exception e) {
    log.warn("[InvalidRequest] {}", e.getMessage());
    return badRequest(ErrorCode.INVALID_INPUT.getMessage());
  }

  /** 지금은 Idempotency-Key가 이 앱의 유일한 필수 헤더라 그 코드로 바로 매핑한다. */
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
    log.warn("[MissingHeader] {}", e.getHeaderName());
    return ResponseEntity.status(ErrorCode.IDEMPOTENCY_KEY_REQUIRED.getStatus())
        .body(ErrorResponse.of(ErrorCode.IDEMPOTENCY_KEY_REQUIRED));
  }

  /**
   * 낙관적 락 충돌을 {@code 409 CONCURRENT_MODIFICATION}으로 응답한다.
   *
   * <p>같은 Asset에 거의 동시에 두 건의 거래가 들어와 두 번째 커밋이 실패한 상황이다. 클라이언트는 재시도하면 된다.
   *
   * @param e 낙관적 락 예외
   * @return 에러 응답
   */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
    log.warn("[OptimisticLock] {}", e.getMessage());
    return ResponseEntity.status(ErrorCode.CONCURRENT_MODIFICATION.getStatus())
        .body(ErrorResponse.of(ErrorCode.CONCURRENT_MODIFICATION));
  }

  /**
   * 유니크 제약 위반을 {@code 409 DUPLICATE_ASSET}으로 응답한다.
   *
   * <p>스키마에 존재하는 유니크 제약은 {@code users.email}과 {@code (user_id, type, symbol)} 뿐이며,
   * 이메일 중복은 서비스에서 먼저 걸러진다. 따라서 여기까지 도달하는 무결성 위반은 사실상 자산 중복 등록의 경합
   * 케이스다.
   *
   * @param e 무결성 위반 예외
   * @return 에러 응답
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
    log.warn("[DataIntegrityViolation] {}", e.getMostSpecificCause().getMessage());
    return ResponseEntity.status(ErrorCode.DUPLICATE_ASSET.getStatus())
        .body(ErrorResponse.of(ErrorCode.DUPLICATE_ASSET));
  }

  /**
   * 지원하지 않는 HTTP 메서드 요청을 405로 응답한다.
   *
   * <p>이 핸들러가 없으면 아래 {@code Exception} 핸들러가 잡아 500 으로 응답한다. 클라이언트 실수를 서버 오류로
   * 보고하는 셈이라, 원인을 찾는 데 불필요한 시간을 쓰게 만든다.
   *
   * @param e 메서드 불일치 예외
   * @return 에러 응답
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(
            new ErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "METHOD_NOT_ALLOWED",
                "지원하지 않는 요청 방식입니다: %s".formatted(e.getMethod())));
  }

  /**
   * 존재하지 않는 경로 요청을 404로 응답한다.
   *
   * @param e 정적 리소스/핸들러를 찾지 못한 예외
   * @return 에러 응답
   */
  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<ErrorResponse> handleNotFound(Exception e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "존재하지 않는 경로입니다."));
  }

  /**
   * 처리되지 않은 예외를 500으로 응답한다.
   *
   * @param e 발생한 예외
   * @return 에러 응답
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    log.error("[UnexpectedException]", e);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
        .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
  }

  private ResponseEntity<ErrorResponse> badRequest(String message) {
    return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
        .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message));
  }

  private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
    Throwable current = throwable;
    while (current != null) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
      current = current.getCause();
    }
    return null;
  }
}
