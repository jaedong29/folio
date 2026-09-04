package com.assetdashboard.domain.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** 사용자가 잘못 입력한 최초 보유 수량을 현재의 실제 보유 수량 기준으로 정정하는 요청. */
public record AssetQuantityCorrectionRequest(
    @Schema(example = "14.7", description = "정정 후 실제 보유 수량")
        @NotNull(message = "실제 보유 수량은 필수입니다.")
        @PositiveOrZero(message = "실제 보유 수량은 0 이상이어야 합니다.")
        BigDecimal quantity) {}
