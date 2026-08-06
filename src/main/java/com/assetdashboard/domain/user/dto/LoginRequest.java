package com.assetdashboard.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 (PRD 4-1).
 *
 * @param email 가입 시 사용한 이메일
 * @param password 평문 비밀번호
 */
public record LoginRequest(
    @Schema(example = "test@example.com") @NotBlank(message = "이메일은 필수입니다.") String email,
    @Schema(example = "1234abcd") @NotBlank(message = "비밀번호는 필수입니다.") String password) {}
