package com.assetdashboard.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.user.dto.EmailAvailabilityResponse;
import com.assetdashboard.domain.user.dto.LoginRequest;
import com.assetdashboard.domain.user.dto.LoginResponse;
import com.assetdashboard.domain.user.dto.SignupRequest;
import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.global.security.IssuedRefreshToken;
import com.assetdashboard.global.security.JwtTokenProvider;
import com.assetdashboard.global.security.RefreshTokenRotation;
import com.assetdashboard.global.security.RefreshTokenService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider tokenProvider;
  @Mock private RefreshTokenService refreshTokenService;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService =
        new UserService(userRepository, passwordEncoder, tokenProvider, refreshTokenService);
  }

  @Test
  void emailAvailabilityNormalizesCaseAndWhitespace() {
    when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

    EmailAvailabilityResponse response =
        userService.checkEmailAvailability("  New@Example.com ");

    assertThat(response.email()).isEqualTo("new@example.com");
    assertThat(response.available()).isTrue();
    assertThat(response.message()).contains("사용 가능");
  }

  @Test
  void emailAvailabilityExplainsDuplicateEmail() {
    when(userRepository.existsByEmail("used@example.com")).thenReturn(true);

    EmailAvailabilityResponse response =
        userService.checkEmailAvailability("used@example.com");

    assertThat(response.available()).isFalse();
    assertThat(response.message()).contains("이미 사용 중");
  }

  @Test
  void signupRaceStillReturnsDuplicateEmailInsteadOfAssetError() {
    SignupRequest request = new SignupRequest("race@example.com", "1234abcd", "race");
    when(userRepository.existsByEmail("race@example.com")).thenReturn(false);
    when(passwordEncoder.encode("1234abcd")).thenReturn("encoded");
    when(userRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("users.email unique"));

    assertThatThrownBy(() -> userService.signup(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));
  }

  @Test
  void loginIssuesAccessTokenAndRefreshTokenTogether() {
    User user = userWithId(7L, "user@example.com", "encoded", "user");
    when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("1234abcd", "encoded")).thenReturn(true);
    when(tokenProvider.createToken(7L)).thenReturn("access-token");
    when(tokenProvider.getExpiresInSeconds()).thenReturn(1800L);
    when(refreshTokenService.issue(7L))
        .thenReturn(new IssuedRefreshToken("refresh-token", Instant.parse("2026-09-20T00:00:00Z")));

    LoginResponse response = userService.login(new LoginRequest("user@example.com", "1234abcd"));

    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isEqualTo("refresh-token");
    assertThat(response.expiresIn()).isEqualTo(1800L);
  }

  @Test
  void refreshRotatesTokenAndReturnsNewPair() {
    User user = userWithId(7L, "user@example.com", "encoded", "user");
    when(refreshTokenService.rotate("old-refresh"))
        .thenReturn(
            new RefreshTokenRotation(
                7L, new IssuedRefreshToken("new-refresh", Instant.parse("2026-09-20T00:00:00Z"))));
    when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    when(tokenProvider.createToken(7L)).thenReturn("new-access");
    when(tokenProvider.getExpiresInSeconds()).thenReturn(1800L);

    LoginResponse response = userService.refresh("old-refresh");

    assertThat(response.accessToken()).isEqualTo("new-access");
    assertThat(response.refreshToken()).isEqualTo("new-refresh");
  }

  @Test
  void refreshFailsWhenRotatedUserNoLongerExists() {
    when(refreshTokenService.rotate("old-refresh"))
        .thenReturn(
            new RefreshTokenRotation(
                99L, new IssuedRefreshToken("new-refresh", Instant.parse("2026-09-20T00:00:00Z"))));
    when(userRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.refresh("old-refresh"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
  }

  @Test
  void logoutDelegatesToRefreshTokenServiceRevoke() {
    userService.logout("some-refresh-token");

    verify(refreshTokenService).revoke(eq("some-refresh-token"));
  }

  private User userWithId(Long id, String email, String encodedPassword, String nickname) {
    User user = User.create(email, encodedPassword, nickname);
    org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
