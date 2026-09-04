package com.assetdashboard.global.security;

/** 회전(rotate)에 성공했을 때 새 Refresh Token과 함께 돌려줄 소유자 id. */
public record RefreshTokenRotation(Long userId, IssuedRefreshToken token) {}
