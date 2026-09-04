package com.assetdashboard.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.Shutdown;

/** 운영 종료 정책이 실제 Spring 설정 객체에 바인딩되는지 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GracefulShutdownConfigurationIntegrationTest {

  @Autowired private ServerProperties serverProperties;

  @Autowired private LifecycleProperties lifecycleProperties;

  @Test
  void waitsForInFlightRequestsForAtMostThirtySeconds() {
    assertThat(serverProperties.getShutdown()).isEqualTo(Shutdown.GRACEFUL);
    assertThat(lifecycleProperties.getTimeoutPerShutdownPhase()).isEqualTo(Duration.ofSeconds(30));
  }
}
