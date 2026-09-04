package com.assetdashboard.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * rotate()가 예외를 던진 뒤에도 재사용 탐지로 인한 family 폐기가 실제로 커밋되는지 확인한다.
 *
 * <p>일부러 클래스 단위 {@code @Transactional}을 쓰지 않는다. 테스트 전체를 하나의 트랜잭션으로 감싸면
 * rotate() 호출들이 그 안에 참여(REQUIRED)할 뿐이라 실제 커밋 경계를 지나지 않고도 같은 세션 안에서는
 * 방금 쓴 값을 그대로 읽어, "예외를 던져도 이 쓰기는 커밋돼야 한다"는 이번 버그의 핵심을 가려버린다.
 */
@SpringBootTest
class RefreshTokenServiceIntegrationTest {

  @Autowired private RefreshTokenService service;
  @Autowired private RefreshTokenRepository repository;

  private Long userId;

  @AfterEach
  void cleanUp() {
    if (userId != null) {
      repository.deleteAllByUserId(userId);
    }
  }

  @Test
  void reusingARotatedTokenPersistsFamilyRevocationEvenThoughRotateThrows() {
    userId = 5_000_000L + (System.nanoTime() % 1_000_000);
    IssuedRefreshToken first = service.issue(userId);
    RefreshTokenRotation rotated = service.rotate(first.rawToken());

    assertThatThrownBy(() -> service.rotate(first.rawToken()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));

    // family 전체가 실제로 폐기됐다면, 방금 정상 회전으로 받은 "새" 토큰도 더는 쓸 수 없어야 한다.
    assertThatThrownBy(() -> service.rotate(rotated.token().rawToken()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
  }

  @Test
  void evictExpiredTokensDeletesOnlyRowsPastTheRetentionWindow() {
    userId = 6_000_000L + (System.nanoTime() % 1_000_000);
    Instant now = Instant.now();
    RefreshToken longExpired =
        RefreshToken.issue(
            userId,
            UUID.randomUUID().toString(),
            "evict-test-" + UUID.randomUUID(),
            now.minus(30, ChronoUnit.DAYS),
            now.minus(10, ChronoUnit.DAYS));
    RefreshToken stillFresh =
        RefreshToken.issue(
            userId,
            UUID.randomUUID().toString(),
            "evict-test-" + UUID.randomUUID(),
            now,
            now.plus(14, ChronoUnit.DAYS));
    repository.save(longExpired);
    repository.save(stillFresh);

    service.evictExpiredTokens();

    assertThat(repository.findByTokenHash(longExpired.getTokenHash())).isEmpty();
    assertThat(repository.findByTokenHash(stillFresh.getTokenHash())).isPresent();
  }
}
