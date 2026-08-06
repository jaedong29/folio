package com.assetdashboard.domain.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 자산 수정 요청 (PRD 4-2). {@code symbol} 과 {@code type} 은 불변이므로 받지 않는다.
 *
 * @param name 새 표시 이름 (null 이면 유지)
 * @param currency 새 통화 코드 (null 이면 유지)
 */
public record AssetUpdateRequest(
    @Schema(example = "내 비트코인") @Size(max = 100, message = "이름은 100자를 넘을 수 없습니다.") String name,
    @Schema(example = "USDT") @Size(max = 10, message = "통화 코드는 10자를 넘을 수 없습니다.")
        String currency) {}
