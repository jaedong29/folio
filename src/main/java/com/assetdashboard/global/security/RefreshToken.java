package com.assetdashboard.global.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 세션 연장에 쓰는 Refresh Token.
 *
 * <p>토큰 원문은 저장하지 않고 SHA-256 해시만 저장한다(비밀번호와 달리 이미 고엔트로피 랜덤값이라 느린 해시가 필요
 * 없다). 회전(rotate)할 때마다 이전 행을 폐기하고 같은 {@code familyId}로 새 행을 만들어, 이미 폐기된 토큰이
 * 재사용되면(탈취 신호) family 전체를 한 번에 폐기할 수 있게 한다.
 */
@Getter
@Entity
@Table(
    name = "refresh_tokens",
    uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "family_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
  private String familyId;

  @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
  private String tokenHash;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  private RefreshToken(
      Long userId, String familyId, String tokenHash, Instant issuedAt, Instant expiresAt) {
    this.userId = userId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
  }

  public static RefreshToken issue(
      Long userId, String familyId, String tokenHash, Instant issuedAt, Instant expiresAt) {
    return new RefreshToken(userId, familyId, tokenHash, issuedAt, expiresAt);
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired(Instant now) {
    return expiresAt.isBefore(now);
  }

  public void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }
}
