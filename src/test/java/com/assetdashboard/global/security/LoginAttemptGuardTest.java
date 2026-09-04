package com.assetdashboard.global.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetdashboard.global.config.AuthRateLimitProperties;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LoginAttemptGuardTest {

  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-09-05T00:00:00Z"));
  private final Clock clock =
      new Clock() {
        @Override
        public java.time.ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now.get();
        }
      };
  private final LoginAttemptGuard guard =
      new LoginAttemptGuard(new AuthRateLimitProperties(5, 15, 20, 60), clock);

  @Test
  void allowsLoginBeforeThresholdIsReached() {
    for (int i = 0; i < 4; i++) {
      guard.recordFailure("user@example.com");
    }

    assertThatCode(() -> guard.ensureNotLocked("user@example.com")).doesNotThrowAnyException();
  }

  @Test
  void locksAfterReachingMaxAttempts() {
    for (int i = 0; i < 5; i++) {
      guard.recordFailure("USER@example.com ");
    }

    assertThatThrownBy(() -> guard.ensureNotLocked(" user@example.com"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS));
  }

  @Test
  void unlocksAutomaticallyAfterLockoutWindowPasses() {
    for (int i = 0; i < 5; i++) {
      guard.recordFailure("user@example.com");
    }
    now.set(now.get().plusSeconds(15 * 60 + 1));

    assertThatCode(() -> guard.ensureNotLocked("user@example.com")).doesNotThrowAnyException();
  }

  @Test
  void successClearsPriorFailures() {
    for (int i = 0; i < 4; i++) {
      guard.recordFailure("user@example.com");
    }
    guard.recordSuccess("user@example.com");
    guard.recordFailure("user@example.com");

    assertThatCode(() -> guard.ensureNotLocked("user@example.com")).doesNotThrowAnyException();
  }

  @Test
  void tracksDifferentEmailsIndependently() {
    for (int i = 0; i < 5; i++) {
      guard.recordFailure("locked@example.com");
    }

    assertThatCode(() -> guard.ensureNotLocked("other@example.com")).doesNotThrowAnyException();
  }
}
