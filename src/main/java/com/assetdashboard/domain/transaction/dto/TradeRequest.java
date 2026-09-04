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
 * <p>{@code exchangeRateMode=AUTO}이면 서버가 자산의 현재 환율을 사용하고, MANUAL이면 요청의
 * {@code exchangeRate}를 사용한다. 과거 외화 거래는 당시 환율을 사용자가 직접 확인해야 하므로 MANUAL만
 * 허용한다. 국내주식은 서버가 항상 1을 사용한다.
 *
 * @param quantity 거래 수량 (0 초과)
 * @param price 거래 단가, 원래 통화 기준 (0 초과)
 * @param exchangeRate 사용자가 직접 입력한 거래 시점 환율. MANUAL일 때 필수
 * @param exchangeRateMode 환율 결정 방식. 생략하면 AUTO
 * @param settlementAssetId 매수대금 차감 또는 매도대금 입금에 사용할 투자 대기자금 Asset id
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
        @Positive(message = "환율은 0보다 커야 합니다.")
        BigDecimal exchangeRate,
    @Schema(example = "MANUAL", description = "AUTO 또는 MANUAL. 생략하면 AUTO")
        ExchangeRateMode exchangeRateMode,
    @Schema(example = "7", description = "같은 통화의 CASH/BANK 자산 id")
        @NotNull(message = "매매대금을 정산할 투자 대기자금은 필수입니다.")
        Long settlementAssetId,
    @Schema(example = "추가 매수") @Size(max = 255, message = "메모는 255자를 넘을 수 없습니다.") String memo,
    @Schema(example = "2026-08-06T10:00:00")
        @NotNull(message = "거래 시점은 필수입니다.")
        @PastOrPresent(message = "미래 시각의 거래는 등록할 수 없습니다.")
        LocalDateTime tradedAt) {}
