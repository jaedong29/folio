package com.assetdashboard.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 발급 설정.
 *
 * @param secret HMAC-SHA 서명 키 (최소 32바이트)
 * @param expirationMinutes 토큰 만료 시간(분). PRD 4-1 기준 24시간 = 1440분
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMinutes) {}
