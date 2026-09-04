package com.assetdashboard.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 발급 설정.
 *
 * <p>Access Token은 짧게 살고 재발급이 실패해도 피해가 작도록 하고, 세션 연장은 서버가 revoke·rotate 할 수 있는
 * Refresh Token이 담당한다.
 *
 * @param secret HMAC-SHA 서명 키 (최소 32바이트)
 * @param expirationMinutes Access Token 만료 시간(분)
 * @param refreshExpirationDays Refresh Token 만료 시간(일)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMinutes, long refreshExpirationDays) {}
