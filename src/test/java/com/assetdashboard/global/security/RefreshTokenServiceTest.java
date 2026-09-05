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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshTokenServiceTest {

  private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
  private final RefreshTokenFamilyRepository familyRepository =
      mock(RefreshTokenFamilyRepository.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);
  private final RefreshTokenService service =
      new RefreshTokenService(
          repository, familyRepository, new JwtProperties("test-secret", 30, 14), clock);

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
    ArgumentCaptor<RefreshTokenFamily> familyCaptor =
        ArgumentCaptor.forClass(RefreshTokenFamily.class);
    verify(familyRepository).save(familyCaptor.capture());
    assertThat(familyCaptor.getValue().getFamilyId()).isEqualTo(saved.getFamilyId());
    assertThat(familyCaptor.getValue().getExpiresAt()).isEqualTo(saved.getExpiresAt());
  }

  @Test
  void issueNormalizesExpirationToDatabaseMicrosecondPrecision() {
    Clock nanosecondClock =
        Clock.fixed(Instant.parse("2026-09-05T00:00:00.123456789Z"), ZoneOffset.UTC);
    RefreshTokenService highPrecisionService =
        new RefreshTokenService(
            repository,
            familyRepository,
            new JwtProperties("test-secret", 30, 14),
            nanosecondClock);

    IssuedRefreshToken issued = highPrecisionService.issue(7L);

    assertThat(issued.expiresAt()).isEqualTo(Instant.parse("2026-09-19T00:00:00.123456Z"));
  }

  @Test
  void rotateRevokesOldTokenAndIssuesNewOneInSameFamily() {
    RefreshToken existing = activeToken(7L, "family-1", "old-hash");
    RefreshTokenFamily family = activeFamily(7L, "family-1");
    when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));
    when(familyRepository.findById("family-1")).thenReturn(Optional.of(family));

    RefreshTokenRotation rotation = service.rotate("raw-old-token");

    assertThat(rotation.userId()).isEqualTo(7L);
    assertThat(existing.isRevoked()).isTrue();
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getFamilyId()).isEqualTo("family-1");
    assertThat(captor.getValue().getExpiresAt()).isEqualTo(family.getExpiresAt());
    assertThat(rotation.token().expiresAt()).isEqualTo(family.getExpiresAt());
    verify(repository, never()).revokeAllByFamilyId(any(), any());
  }

  @Test
  void reusingAnAlreadyRotatedTokenRevokesTheWholeFamily() {
    RefreshToken alreadyRevoked = activeToken(7L, "family-1", "stolen-hash");
    RefreshTokenFamily family = activeFamily(7L, "family-1");
    alreadyRevoked.revoke(Instant.parse("2026-09-04T00:00:00Z"));
    when(repository.findByTokenHash(any())).thenReturn(Optional.of(alreadyRevoked));
    when(familyRepository.findById("family-1")).thenReturn(Optional.of(family));

    assertThatThrownBy(() -> service.rotate("stolen-raw-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));

    verify(repository).revokeAllByFamilyId(eq("family-1"), any());
    assertThat(family.isRevoked()).isTrue();
  }

  @Test
  void activeTokenInARevokedFamilyCannotBeRotated() {
    RefreshToken existing = activeToken(7L, "family-1", "active-hash");
    RefreshTokenFamily family = activeFamily(7L, "family-1");
    family.revoke(Instant.parse("2026-09-04T00:00:00Z"));
    when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));
    when(familyRepository.findById("family-1")).thenReturn(Optional.of(family));

    assertThatThrownBy(() -> service.rotate("raw-active-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));

    verify(repository).revokeAllByFamilyId(eq("family-1"), any());
    verify(repository, never()).save(any());
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
    when(familyRepository.findById("family-1"))
        .thenReturn(Optional.of(activeFamily(7L, "family-1")));

    assertThatThrownBy(() -> service.rotate("expired-raw-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
  }

  @Test
  void rotatingATokenPastTheFamilyAbsoluteExpirationFails() {
    RefreshToken token = activeToken(7L, "family-1", "active-hash");
    RefreshTokenFamily expiredFamily =
        RefreshTokenFamily.issue(
            "family-1",
            7L,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-09-04T00:00:00Z"));
    when(repository.findByTokenHash(any())).thenReturn(Optional.of(token));
    when(familyRepository.findById("family-1")).thenReturn(Optional.of(expiredFamily));

    assertThatThrownBy(() -> service.rotate("raw-active-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));

    verify(repository, never()).save(any());
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
    RefreshTokenFamily family = activeFamily(7L, "family-1");
    when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));
    when(familyRepository.findById("family-1")).thenReturn(Optional.of(family));

    service.revoke("raw-token");

    assertThat(existing.isRevoked()).isTrue();
    assertThat(family.isRevoked()).isTrue();
    verify(repository).revokeAllByFamilyId(eq("family-1"), any());
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
    verify(familyRepository).revokeAllByUserId(eq(7L), any());
  }

  @Test
  void evictExpiredTokensDeletesOnlyFamiliesPastTheRetentionWindowAndTheirTokens() {
    when(familyRepository.findIdsByExpiresAtBefore(Instant.parse("2026-08-29T00:00:00Z")))
        .thenReturn(List.of("expired-family"));

    service.evictExpiredTokens();

    verify(repository).deleteAllByFamilyIdIn(List.of("expired-family"));
    verify(familyRepository).deleteAllByFamilyIdIn(List.of("expired-family"));
  }

  @Test
  void evictExpiredTokensKeepsEveryTokenHashWhenNoFamilyHasExpired() {
    when(familyRepository.findIdsByExpiresAtBefore(any())).thenReturn(List.of());

    service.evictExpiredTokens();

    verify(repository, never()).deleteAllByFamilyIdIn(any());
    verify(familyRepository, never()).deleteAllByFamilyIdIn(any());
  }

  @Test
  void deleteAllForUserDeletesTokensBeforeFamilyMetadata() {
    service.deleteAllForUser(7L);

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(repository, familyRepository);
    order.verify(repository).deleteAllByUserId(7L);
    order.verify(familyRepository).deleteAllByUserId(7L);
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

  private RefreshTokenFamily activeFamily(Long userId, String familyId) {
    return RefreshTokenFamily.issue(
        familyId,
        userId,
        Instant.parse("2026-09-01T00:00:00Z"),
        Instant.parse("2026-09-19T00:00:00Z"));
  }
}
