package com.assetdashboard.domain.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 환율 수동 갱신 요청 (PRD 4-2).
 *
 * <p>해외주식의 환율은 MVP 에서 수동 입력이다(CRYPTO 는 Upbit 로 자동 조회).
 *
 * @param exchangeRate 원/통화 환율
 */
public record AssetExchangeRateUpdateRequest(
    @Schema(example = "1390.0")
        @NotNull(message = "환율은 필수입니다.")
        @Positive(message = "환율은 0보다 커야 합니다.")
        BigDecimal exchangeRate) {}
