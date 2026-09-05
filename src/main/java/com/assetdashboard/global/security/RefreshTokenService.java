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
import java.util.List;
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

  // family 절대 만료 뒤에도 운영 확인을 위해 잠시 보존한 후 token hash와 family를 함께 정리한다.
  private static final long FAMILY_RETENTION_DAYS = 7;

  private final RefreshTokenRepository repository;
  private final RefreshTokenFamilyRepository familyRepository;
  private final JwtProperties properties;
  private final Clock clock;

  @Transactional
  public IssuedRefreshToken issue(Long userId) {
    Instant now = Instant.now(clock);
    Instant expiresAt = now.plus(properties.refreshExpirationDays(), ChronoUnit.DAYS);
    RefreshTokenFamily family =
        RefreshTokenFamily.issue(UUID.randomUUID().toString(), userId, now, expiresAt);
    familyRepository.save(family);
    return issueToken(family, now);
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
    RefreshTokenFamily family =
        familyRepository
            .findById(existing.getFamilyId())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
    Instant now = Instant.now(clock);

    if (existing.isRevoked() || family.isRevoked()) {
      log.warn(
          "[RefreshToken] 이미 폐기된 토큰이 재사용됐습니다. familyId={} — family 전체를 폐기합니다.",
          existing.getFamilyId());
      family.revoke(now);
      repository.revokeAllByFamilyId(existing.getFamilyId(), now);
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
    if (existing.isExpired(now) || family.isExpired(now)) {
      throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    existing.revoke(now);
    IssuedRefreshToken next = issueToken(family, now);
    return new RefreshTokenRotation(existing.getUserId(), next);
  }

  @Transactional
  public void revoke(String rawToken) {
    repository
        .findByTokenHash(hash(rawToken))
        .ifPresent(
            token -> {
              Instant now = Instant.now(clock);
              token.revoke(now);
              familyRepository.findById(token.getFamilyId()).ifPresent(family -> family.revoke(now));
              repository.revokeAllByFamilyId(token.getFamilyId(), now);
            });
  }

  /** 비밀번호 변경·회원 탈퇴처럼 다른 모든 세션을 강제로 끊어야 할 때 쓴다. */
  @Transactional
  public void revokeAllForUser(Long userId) {
    Instant now = Instant.now(clock);
    familyRepository.revokeAllByUserId(userId, now);
    repository.revokeAllByUserId(userId, now);
  }

  /**
   * 절대 만료된 지 오래된 family와 그 token hash들을 함께 지운다.
   *
   * <p>개별 token의 만료 시각만 보고 지우면 같은 family의 최신 token이 살아 있는 동안 과거 token의 재사용을
   * 탐지하지 못한다. 따라서 활성 family의 token hash는 모두 유지하고, family 절대 만료 뒤에만 일괄 정리한다.
   */
  @Scheduled(fixedRate = 86_400_000)
  @Transactional
  public void evictExpiredTokens() {
    Instant cutoff = Instant.now(clock).minus(FAMILY_RETENTION_DAYS, ChronoUnit.DAYS);
    List<String> expiredFamilyIds = familyRepository.findIdsByExpiresAtBefore(cutoff);
    if (!expiredFamilyIds.isEmpty()) {
      int deletedTokens = repository.deleteAllByFamilyIdIn(expiredFamilyIds);
      int deletedFamilies = familyRepository.deleteAllByFamilyIdIn(expiredFamilyIds);
      log.info(
          "[RefreshToken] 절대 만료 후 {}일 지난 family {}건과 token {}건을 정리했습니다.",
          FAMILY_RETENTION_DAYS,
          deletedFamilies,
          deletedTokens);
    }
  }

  /** 회원 탈퇴 시 token을 먼저 지우고 family 메타데이터까지 제거한다. */
  @Transactional
  public void deleteAllForUser(Long userId) {
    repository.deleteAllByUserId(userId);
    familyRepository.deleteAllByUserId(userId);
  }

  private IssuedRefreshToken issueToken(RefreshTokenFamily family, Instant now) {
    String raw = generateToken();
    repository.save(
        RefreshToken.issue(
            family.getUserId(),
            family.getFamilyId(),
            hash(raw),
            now,
            family.getExpiresAt()));
    return new IssuedRefreshToken(raw, family.getExpiresAt());
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
