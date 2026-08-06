package com.assetdashboard.domain.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 매수·매도 요청 (PRD 4-3).
 *
 * <p>매수와 매도의 필드 구성이 완전히 동일하므로 하나의 DTO 를 공유한다. 두 요청은 서로 다른 엔드포인트로 구분되며,
 * 어떤 도메인 메서드를 부를지는 URL 이 결정한다. 같은 필드를 가진 클래스를 두 개 두면 검증 규칙을 한쪽에만 고치는
 * 실수가 생긴다.
 *
 * <p>{@code exchangeRate} 는 <b>매도에도 필수</b>다. 실현손익을 KRW 로 확정하려면 매도 시점의 환율이 필요하다.
 * 국내주식·원화현금은 1을 넘긴다.
 *
 * @param quantity 거래 수량 (0 초과)
 * @param price 거래 단가, 원래 통화 기준 (0 초과)
 * @param exchangeRate 거래 시점 환율 (0 초과)
 * @param memo 사용자 메모 (선택)
 * @param tradedAt 거래 시점. 미래 시각 불가
 */
public record TradeRequest(
    @Schema(example = "0.1")
        @NotNull(message = "수량은 필수입니다.")
        @Positive(message = "수량은 0보다 커야 합니다.")
        BigDecimal quantity,
    @Schema(example = "40000")
        @NotNull(message = "가격은 필수입니다.")
        @Positive(message = "가격은 0보다 커야 합니다.")
        BigDecimal price,
    @Schema(example = "1380", description = "국내주식·원화현금은 1")
        @NotNull(message = "환율은 필수입니다.")
        @Positive(message = "환율은 0보다 커야 합니다.")
        BigDecimal exchangeRate,
    @Schema(example = "추가 매수") @Size(max = 255, message = "메모는 255자를 넘을 수 없습니다.") String memo,
    @Schema(example = "2026-08-06T10:00:00")
        @NotNull(message = "거래 시점은 필수입니다.")
        @PastOrPresent(message = "미래 시각의 거래는 등록할 수 없습니다.")
        LocalDateTime tradedAt) {}
