package com.assetdashboard.domain.transaction.controller;

import com.assetdashboard.domain.transaction.dto.CashFlowRequest;
import com.assetdashboard.domain.transaction.dto.TradeRequest;
import com.assetdashboard.domain.transaction.dto.TransactionHistoryResponse;
import com.assetdashboard.domain.transaction.dto.TransactionResponse;
import com.assetdashboard.domain.transaction.service.TransactionService;
import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 거래 API (PRD 4-3).
 *
 * <p>모든 경로가 {@code /api/assets/{assetId}/transactions/*} 형태로 Asset 에 종속된다. Transaction 을
 * 조회·변경의 시작점으로 삼는 API({@code GET /api/transactions})는 만들지 않는다 — Asset 이 유일한 Aggregate
 * Root 라는 설계를 URL 구조에서부터 드러내기 위함이다.
 */
@Tag(name = "Transaction", description = "매수 / 매도 / 입금 / 출금 / 거래내역")
@RestController
@RequestMapping("/api/assets/{assetId}/transactions")
@RequiredArgsConstructor
public class TransactionController {

  private final TransactionService transactionService;

  /**
   * 매수를 기록한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @param request 매수 요청
   * @param idempotencyKey 클라이언트가 재시도 시 그대로 다시 보내는 요청 id. 같은 값이면 두 번 체결되지 않는다
   * @return 생성된 거래와 반영 후 자산 상태 (201)
   */
  @Operation(
      summary = "매수",
      description = "평균 매입 단가가 재계산된다. STOCK/CRYPTO 전용. "
          + "Idempotency-Key 헤더가 같으면 재시도해도 한 번만 체결된다.")
  @PostMapping("/buy")
  public ResponseEntity<TransactionResponse> buy(
      @CurrentUserId Long userId,
      @PathVariable Long assetId,
      @Valid @RequestBody TradeRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(transactionService.buy(userId, assetId, request, idempotencyKey));
  }

  /**
   * 매도를 기록한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @param request 매도 요청
   * @return 생성된 거래와 반영 후 자산 상태 (201)
   */
  @Operation(
      summary = "매도",
      description = "실현손익이 누적되고 평균 매입 단가는 유지된다. 보유 수량 초과 시 400 INSUFFICIENT_ASSET_QUANTITY. "
          + "Idempotency-Key 헤더가 같으면 재시도해도 한 번만 체결된다.")
  @PostMapping("/sell")
  public ResponseEntity<TransactionResponse> sell(
      @CurrentUserId Long userId,
      @PathVariable Long assetId,
      @Valid @RequestBody TradeRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(transactionService.sell(userId, assetId, request, idempotencyKey));
  }

  /**
   * 입금을 기록한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @param request 입금 요청
   * @return 생성된 거래와 반영 후 자산 상태 (201)
   */
  @Operation(
      summary = "입금",
      description = "CASH/BANK 전용. Idempotency-Key 헤더가 같으면 재시도해도 한 번만 반영된다.")
  @PostMapping("/deposit")
  public ResponseEntity<TransactionResponse> deposit(
      @CurrentUserId Long userId,
      @PathVariable Long assetId,
      @Valid @RequestBody CashFlowRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(transactionService.deposit(userId, assetId, request, idempotencyKey));
  }

  /**
   * 출금을 기록한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @param request 출금 요청
   * @return 생성된 거래와 반영 후 자산 상태 (201)
   */
  @Operation(
      summary = "출금",
      description = "CASH/BANK 전용. 잔액 부족 시 400 INSUFFICIENT_ASSET_QUANTITY. "
          + "Idempotency-Key 헤더가 같으면 재시도해도 한 번만 반영된다.")
  @PostMapping("/withdraw")
  public ResponseEntity<TransactionResponse> withdraw(
      @CurrentUserId Long userId,
      @PathVariable Long assetId,
      @Valid @RequestBody CashFlowRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(transactionService.withdraw(userId, assetId, request, idempotencyKey));
  }

  /**
   * 잘못 입력한 거래를 삭제하고 자산 상태를 다시 계산한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @param transactionId 삭제할 거래 id
   * @return 재계산된 자산 상태
   */
  @Operation(
      summary = "거래 삭제 (오입력 정정)",
      description =
          "거래를 지우고 남은 이력을 tradedAt 순으로 재생해 수량·평단가·실현손익을 다시 계산한다. "
              + "삭제 시 이후 거래의 보유 수량이 음수가 되면 400 으로 거부한다.")
  @DeleteMapping("/{transactionId}")
  public ResponseEntity<TransactionResponse.AssetSnapshot> delete(
      @CurrentUserId Long userId,
      @PathVariable Long assetId,
      @PathVariable Long transactionId) {
    return ResponseEntity.ok(transactionService.delete(userId, assetId, transactionId));
  }

  /**
   * 특정 자산의 거래 내역을 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 대상 자산 id
   * @return 최신순 거래 내역
   */
  @Operation(summary = "거래 내역 조회", description = "특정 Asset 의 거래만 조회한다. 전체 거래 내역 API 는 제공하지 않는다.")
  @GetMapping
  public ResponseEntity<List<TransactionHistoryResponse>> getHistory(
      @CurrentUserId Long userId, @PathVariable Long assetId) {
    return ResponseEntity.ok(transactionService.getHistory(userId, assetId));
  }
}
