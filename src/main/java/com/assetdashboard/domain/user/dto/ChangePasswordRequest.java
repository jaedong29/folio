package com.assetdashboard.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 현재 비밀번호를 확인한 뒤 새 비밀번호로 변경하기 위한 요청. */
public record ChangePasswordRequest(
    @Schema(example = "old-password")
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,
    @Schema(example = "new-password")
        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, message = "새 비밀번호는 8자 이상이어야 합니다.")
        String newPassword) {}
