package com.assetdashboard.global.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 번의 로그인에서 시작된 Refresh Token 회전 계보의 보안 상태.
 *
 * <p>개별 token hash와 분리해 family의 절대 만료와 폐기 여부를 한 행에 보존한다. 새 토큰으로 계속 회전해도
 * {@code expiresAt}은 늘어나지 않으며, 이 시각이 지난 뒤에만 해당 family의 token hash들을 함께 정리할 수 있다.
 */
@Getter
@Entity
@Table(name = "refresh_token_families")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenFamily {

  @Id
  @Column(name = "family_id", length = 36)
  private String familyId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  private RefreshTokenFamily(
      String familyId, Long userId, Instant issuedAt, Instant expiresAt) {
    this.familyId = familyId;
    this.userId = userId;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
  }

  public static RefreshTokenFamily issue(
      String familyId, Long userId, Instant issuedAt, Instant expiresAt) {
    return new RefreshTokenFamily(familyId, userId, issuedAt, expiresAt);
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
