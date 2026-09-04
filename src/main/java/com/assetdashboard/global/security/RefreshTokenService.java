package com.assetdashboard.global.security;

import com.assetdashboard.global.config.JwtProperties;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token 발급·회전(rotate)·폐기를 담당한다.
 *
 * <p>매 회전마다 이전 토큰을 폐기하고 같은 family에 새 토큰을 발급한다. 이미 폐기된(=한 번 회전에 쓰인) 토큰이
 * 다시 제시되면 탈취로 간주해 그 family의 모든 토큰을 폐기하고 재로그인을 요구한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  // 만료된 토큰도 재사용 탐지(rotate()가 revoked 여부를 expired보다 먼저 검사)에 잠시 쓰일 수 있어 바로
  // 지우지 않는다. 만료 후 이 기간이 지나면 그 신호도 의미가 없어지므로 그때 정리한다.
  private static final long EXPIRED_RETENTION_DAYS = 7;

  private final RefreshTokenRepository repository;
  private final JwtProperties properties;
  private final Clock clock;

  @Transactional
  public IssuedRefreshToken issue(Long userId) {
    return issue(userId, UUID.randomUUID().toString());
  }

  // 재사용 탐지 시 family 전체를 폐기한 뒤 예외를 던진다. 이 메서드를 호출하는 UserService.refresh()도
  // 자체 트랜잭션을 갖고 있어서, 같은 트랜잭션에 그냥 참여(REQUIRED)하면 그쪽의 기본 롤백 규칙이 이 메서드의
  // noRollbackFor보다 나중에 적용되어 폐기 자체가 롤백돼버린다. REQUIRES_NEW로 독립된 트랜잭션을 떠서 호출자가
  // 무엇을 하든 이 폐기가 항상 커밋되게 한다.
  @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BusinessException.class)
  public RefreshTokenRotation rotate(String rawToken) {
    RefreshToken existing =
        repository
            .findByTokenHash(hash(rawToken))
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
    Instant now = Instant.now(clock);

    if (existing.isRevoked()) {
      log.warn(
          "[RefreshToken] 이미 폐기된 토큰이 재사용됐습니다. familyId={} — family 전체를 폐기합니다.",
          existing.getFamilyId());
      repository.revokeAllByFamilyId(existing.getFamilyId(), now);
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
    if (existing.isExpired(now)) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    existing.revoke(now);
    IssuedRefreshToken next = issue(existing.getUserId(), existing.getFamilyId());
    return new RefreshTokenRotation(existing.getUserId(), next);
  }

  @Transactional
  public void revoke(String rawToken) {
    repository.findByTokenHash(hash(rawToken)).ifPresent(t -> t.revoke(Instant.now(clock)));
  }

  /** 비밀번호 변경·회원 탈퇴처럼 다른 모든 세션을 강제로 끊어야 할 때 쓴다. */
  @Transactional
  public void revokeAllForUser(Long userId) {
    repository.revokeAllByUserId(userId, Instant.now(clock));
  }

  /** 만료된 지 오래된 토큰을 지운다. 회전·재사용 탐지는 실행 시점의 행 존재 여부에 의존하지 않으므로 안전하다. */
  @Scheduled(fixedRate = 86_400_000)
  @Transactional
  public void evictExpiredTokens() {
    Instant cutoff = Instant.now(clock).minus(EXPIRED_RETENTION_DAYS, ChronoUnit.DAYS);
    int deleted = repository.deleteAllByExpiresAtBefore(cutoff);
    if (deleted > 0) {
      log.info("[RefreshToken] 만료 후 {}일 지난 토큰 {}건을 정리했습니다.", EXPIRED_RETENTION_DAYS, deleted);
    }
  }

  private IssuedRefreshToken issue(Long userId, String familyId) {
    String raw = generateToken();
    Instant now = Instant.now(clock);
    Instant expiresAt = now.plus(properties.refreshExpirationDays(), ChronoUnit.DAYS);
    repository.save(RefreshToken.issue(userId, familyId, hash(raw), now, expiresAt));
    return new IssuedRefreshToken(raw, expiresAt);
  }

  private String generateToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String rawToken) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
    }
  }
}
