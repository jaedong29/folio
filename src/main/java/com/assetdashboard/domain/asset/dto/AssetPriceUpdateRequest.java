package com.assetdashboard.domain.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 현재가 수동 갱신 요청 (PRD 4-2).
 *
 * <p>자동 조회를 신뢰하지 못하거나 외부 API 가 죽었을 때의 최후 폴백 경로다.
 *
 * @param currentPrice 원래 통화 기준 현재가
 */
public record AssetPriceUpdateRequest(
    @Schema(example = "56000000")
        @NotNull(message = "현재가는 필수입니다.")
        @Positive(message = "현재가는 0보다 커야 합니다.")
        BigDecimal currentPrice) {}
