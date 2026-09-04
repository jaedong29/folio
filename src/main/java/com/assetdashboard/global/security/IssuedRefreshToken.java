package com.assetdashboard.global.security;

import java.time.Instant;

/** 발급 직후 한 번만 평문으로 존재하는 Refresh Token. 이후에는 해시로만 남는다. */
public record IssuedRefreshToken(String rawToken, Instant expiresAt) {}
