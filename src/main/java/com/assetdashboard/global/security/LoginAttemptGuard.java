package com.assetdashboard.global.security;

import com.assetdashboard.global.config.AuthRateLimitProperties;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 같은 이메일로 반복되는 로그인 실패를 잠가 brute force를 늦춘다.
 *
 * <p>이메일당 실패 횟수는 잠금이 걸리거나 로그인에 성공하기 전까지는 시간이 지나도 저절로 줄지 않는다 —
 * 공격자가 며칠에 걸쳐 시도 간격을 벌려 카운터를 우회하지 못하게 하기 위해서다. 단일 인스턴스 MVP를
 * 전제로 메모리에만 상태를 둔다. 인스턴스를 여러 대로 늘리려면 Redis 같은 공유 저장소가 필요하다.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

  private static final long STALE_ENTRY_HOURS = 1;

  private final AuthRateLimitProperties properties;
  private final Clock clock;
  private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

  public void ensureNotLocked(String email) {
    Attempt attempt = attempts.get(normalize(email));
    if (attempt != null && isLocked(attempt, Instant.now(clock))) {
      throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
    }
  }

  public void recordFailure(String email) {
    Instant now = Instant.now(clock);
    attempts.compute(
        normalize(email),
        (key, existing) -> {
          int failures = (existing == null || isStale(existing, now)) ? 1 : existing.failureCount + 1;
          Instant lockedUntil =
              failures >= properties.maxLoginAttempts()
                  ? now.plus(properties.loginLockoutMinutes(), ChronoUnit.MINUTES)
                  : null;
          return new Attempt(failures, lockedUntil, now);
        });
  }

  public void recordSuccess(String email) {
    attempts.remove(normalize(email));
  }

  /** 단일 인스턴스 MVP에서 메모리가 무한히 늘어나지 않도록 오래된 항목을 정리한다. */
  @Scheduled(fixedRate = 600_000)
  void evictStaleEntries() {
    Instant now = Instant.now(clock);
    attempts.entrySet().removeIf(entry -> isStale(entry.getValue(), now));
  }

  private boolean isLocked(Attempt attempt, Instant now) {
    return attempt.lockedUntil != null && attempt.lockedUntil.isAfter(now);
  }

  private boolean isStale(Attempt attempt, Instant now) {
    Instant referencePoint = attempt.lockedUntil != null ? attempt.lockedUntil : attempt.updatedAt;
    return referencePoint.plus(STALE_ENTRY_HOURS, ChronoUnit.HOURS).isBefore(now);
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private record Attempt(int failureCount, Instant lockedUntil, Instant updatedAt) {}
}
