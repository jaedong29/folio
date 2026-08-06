package com.assetdashboard.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 (PRD 4-1).
 *
 * @param email 이메일 형식이어야 한다
 * @param password 8자 이상 (특수문자 강제 없음)
 * @param nickname 표시용 닉네임, 최대 50자
 */
public record SignupRequest(
    @Schema(example = "test@example.com")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,
    @Schema(example = "1234abcd")
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,
    @Schema(example = "재동")
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 50, message = "닉네임은 50자를 넘을 수 없습니다.")
        String nickname) {}
