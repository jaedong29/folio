package com.assetdashboard.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 API의 brute force 방어 설정.
 *
 * <p>단일 인스턴스 개인 MVP를 전제로 메모리에만 상태를 둔다. 인스턴스를 여러 대로 늘리면 Redis 같은 공유
 * 저장소로 옮겨야 한다(docs/PRODUCTION_READINESS.md 참고).
 *
 * @param maxLoginAttempts 이 횟수만큼 같은 이메일로 로그인에 실패하면 잠근다
 * @param loginLockoutMinutes 잠금 유지 시간(분)
 * @param maxRequestsPerIpPerWindow 같은 IP가 {@code ipWindowSeconds} 동안 보낼 수 있는 인증 API 요청 상한
 * @param ipWindowSeconds IP 요청 수를 세는 고정 윈도우 크기(초)
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthRateLimitProperties(
    int maxLoginAttempts,
    long loginLockoutMinutes,
    int maxRequestsPerIpPerWindow,
    long ipWindowSeconds) {}
