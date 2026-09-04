package com.assetdashboard.domain.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 환율 수동 보정 요청 (PRD 4-2).
 *
 * <p>평상시에는 USD/KRW와 USDT/KRW를 자동 조회한다. 이 요청은 외부 조회 실패나 과거 값 보정용 폴백이다.
 *
 * @param exchangeRate 원/통화 환율
 */
public record AssetExchangeRateUpdateRequest(
    @Schema(example = "1390.0")
        @NotNull(message = "환율은 필수입니다.")
        @Positive(message = "환율은 0보다 커야 합니다.")
        BigDecimal exchangeRate) {}
