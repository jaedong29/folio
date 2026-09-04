package com.assetdashboard.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원가입 이메일 중복확인 결과.
 *
 * @param email 소문자로 정규화된 이메일
 * @param available 가입에 사용할 수 있으면 true
 * @param message 화면에 바로 보여줄 안내 문구
 */
public record EmailAvailabilityResponse(
    @Schema(example = "user@example.com") String email,
    @Schema(example = "true") boolean available,
    @Schema(example = "사용 가능한 이메일입니다.") String message) {}
