package com.assetdashboard.domain.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 입금·출금 요청 (PRD 4-3).
 *
 * <p>현금성 자산은 "보유 금액이 곧 수량"이므로 단가와 환율을 받지 않는다.
 *
 * @param quantity 입출금액 (0 초과)
 * @param memo 사용자 메모 (선택)
 * @param tradedAt 거래 시점. 미래 시각 불가
 */
public record CashFlowRequest(
    @Schema(example = "1000000")
        @NotNull(message = "금액은 필수입니다.")
        @Positive(message = "금액은 0보다 커야 합니다.")
        BigDecimal quantity,
    @Schema(example = "월급 입금") @Size(max = 255, message = "메모는 255자를 넘을 수 없습니다.") String memo,
    @Schema(example = "2026-08-06T10:00:00")
        @NotNull(message = "거래 시점은 필수입니다.")
        @PastOrPresent(message = "미래 시각의 거래는 등록할 수 없습니다.")
        LocalDateTime tradedAt) {}
