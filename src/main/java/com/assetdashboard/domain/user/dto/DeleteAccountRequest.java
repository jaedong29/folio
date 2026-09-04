package com.assetdashboard.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 계정과 연결된 모든 데이터를 삭제하기 위한 확인 요청. */
public record DeleteAccountRequest(
    @Schema(example = "current-password")
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String password,
    @Schema(example = "DELETE")
        @NotBlank(message = "탈퇴 확인 문구는 필수입니다.")
        String confirmation) {}
