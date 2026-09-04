package com.assetdashboard.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetdashboard.global.config.JwtProperties;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshTokenServiceTest {

  private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);
  private final RefreshTokenService service =
      new RefreshTokenService(repository, new JwtProperties("test-secret", 30, 14), clock);

  @Test
  void issuesRandomTokenAndStoresOnlyItsHash() {
    IssuedRefreshToken issued = service.issue(7L);

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(repository).save(captor.capture());
    RefreshToken saved = captor.getValue();
    assertThat(issued.rawToken()).isNotBlank();
    assertThat(saved.getTokenHash()).isNotEqualTo(issued.rawToken());
    assertThat(saved.getTokenHash()).hasSize(64); // SHA-256 hex
    assertThat(saved.getUserId()).isEqualTo(7L);
    assertThat(saved.getExpiresAt()).isEqualTo(Instant.parse("2026-09-19T00:00:00Z"));
  }

  @Test
  void rotateRevokesOldTokenAndIssuesNewOneInSameFamily() {
    RefreshToken existing = activeToken(7L, "family-1", "old-hash");
    when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));

    RefreshTokenRotation rotation = service.rotate("raw-old-token");

    assertThat(rotation.userId()).isEqualTo(7L);
    assertThat(existing.isRevoked()).isTrue();
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getFamilyId()).isEqualTo("family-1");
    verify(repository, never()).revokeAllByFamilyId(any(), any());
  }

  @Test
  void reusingAnAlreadyRotatedTokenRevokesTheWholeFamily() {
    RefreshToken alreadyRevoked = activeToken(7L, "family-1", "stolen-hash");
    alreadyRevoked.revoke(Instant.parse("2026-09-04T00:00:00Z"));
    when(repository.findByTokenHash(any())).thenReturn(Optional.of(alreadyRevoked));

    assertThatThrownBy(() -> service.rotate("stolen-raw-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));

    verify(repository).revokeAllByFamilyId(eq("family-1"), any());
  }

  @Test
  void rotatingAnExpiredTokenFails() {
    RefreshToken expired =
        RefreshToken.issue(
            7L,
            "family-1",
            "expired-hash",
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-09-01T00:00:00Z"));
    when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.rotate("expired-raw-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
  }

  @Test
  void rotatingAnUnknownTokenFails() {
    when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rotate("unknown-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
  }

  @Test
  void revokeMarksMatchingTokenRevoked() {
    RefreshToken existing = activeToken(7L, "family-1", "hash");
    when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));

    service.revoke("raw-token");

    assertThat(existing.isRevoked()).isTrue();
  }

  @Test
  void revokeIsANoOpWhenTokenIsUnknown() {
    when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

    service.revoke("unknown-token");
  }

  @Test
  void revokeAllForUserDelegatesToRepository() {
    service.revokeAllForUser(7L);

    verify(repository).revokeAllByUserId(eq(7L), any());
  }

  private RefreshToken activeToken(Long userId, String familyId, String tokenHash) {
    RefreshToken token =
        RefreshToken.issue(
            userId,
            familyId,
            tokenHash,
            Instant.parse("2026-09-01T00:00:00Z"),
            Instant.parse("2026-09-15T00:00:00Z"));
    ReflectionTestUtils.setField(token, "id", 1L);
    return token;
  }
}
