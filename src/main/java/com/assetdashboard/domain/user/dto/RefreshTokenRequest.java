package com.assetdashboard.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 토큰 재발급·로그아웃 요청.
 *
 * @param refreshToken 로그인 또는 이전 재발급에서 받은 Refresh Token
 */
public record RefreshTokenRequest(
    @NotBlank(message = "refreshToken은 필수입니다.") String refreshToken) {}
