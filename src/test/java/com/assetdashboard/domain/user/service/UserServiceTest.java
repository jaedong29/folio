package com.assetdashboard.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.user.dto.EmailAvailabilityResponse;
import com.assetdashboard.domain.user.dto.SignupRequest;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.global.security.JwtTokenProvider;
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

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepository, passwordEncoder, tokenProvider);
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
}
