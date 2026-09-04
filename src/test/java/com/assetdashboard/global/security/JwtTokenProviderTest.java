package com.assetdashboard.global.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetdashboard.global.config.JwtProperties;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  @Test
  void blankSecretFailsWithAnOperationalMessage() {
    assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("", 60, 14)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_JWT_SECRET");
  }

  @Test
  void shortSecretFailsBeforeTheJwtLibraryDoes() {
    assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("too-short", 60, 14)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }
}
