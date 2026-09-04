package com.assetdashboard.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.assetdashboard.global.config.AuthRateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);
  private final AuthRateLimitFilter filter =
      new AuthRateLimitFilter(
          new AuthRateLimitProperties(5, 15, 3, 60), new ObjectMapper(), clock);

  @Test
  void passesRequestsThroughUnderTheLimit() throws Exception {
    FilterChain chain = mock(FilterChain.class);

    for (int i = 0; i < 3; i++) {
      MockHttpServletRequest request = authRequest("10.0.0.1");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, chain);
      assertThat(response.getStatus()).isEqualTo(200);
    }

    verify(chain, times(3)).doFilter(any(), any());
  }

  @Test
  void blocksTheRequestThatCrossesTheLimit() throws Exception {
    FilterChain chain = mock(FilterChain.class);
    for (int i = 0; i < 3; i++) {
      filter.doFilter(authRequest("10.0.0.2"), new MockHttpServletResponse(), chain);
    }

    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilter(authRequest("10.0.0.2"), blocked, chain);

    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(blocked.getContentAsString()).contains("TOO_MANY_AUTH_REQUESTS");
  }

  @Test
  void tracksEachIpAddressIndependently() throws Exception {
    FilterChain chain = mock(FilterChain.class);
    for (int i = 0; i < 3; i++) {
      filter.doFilter(authRequest("10.0.0.3"), new MockHttpServletResponse(), chain);
    }

    MockHttpServletResponse otherIp = new MockHttpServletResponse();
    filter.doFilter(authRequest("10.0.0.4"), otherIp, chain);

    assertThat(otherIp.getStatus()).isEqualTo(200);
  }

  @Test
  void doesNotRateLimitNonAuthPaths() throws Exception {
    FilterChain chain = mock(FilterChain.class);
    for (int i = 0; i < 10; i++) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard");
      request.setRemoteAddr("10.0.0.5");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, chain);
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  private MockHttpServletRequest authRequest(String ip) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.setRemoteAddr(ip);
    return request;
  }
}
