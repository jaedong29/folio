package com.assetdashboard.global.security;

import com.assetdashboard.global.config.AuthRateLimitProperties;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.global.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * IP 하나가 인증 API({@code /api/auth/**})를 짧은 시간에 너무 많이 두드리지 못하게 막는다.
 *
 * <p>{@link LoginAttemptGuard}가 "같은 이메일 brute force"를 막는다면, 이 필터는 "같은 IP가 여러
 * 이메일을 돌아가며 시도하거나 API를 단순히 스팸하는" 경우를 막는다. 리버스 프록시 뒤에 있지 않은 단일
 * 인스턴스 배포를 전제로 {@link HttpServletRequest#getRemoteAddr()}를 그대로 신뢰한다 — 프록시를 두면
 * {@code X-Forwarded-For} 신뢰 체인을 별도로 구성해야 한다.
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private static final String PATH_PREFIX = "/api/auth/";
  private static final long STALE_WINDOW_MULTIPLIER = 10;

  private final AuthRateLimitProperties properties;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    if (request.getRequestURI().startsWith(PATH_PREFIX) && exceedsLimit(request.getRemoteAddr())) {
      writeTooManyRequests(response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean exceedsLimit(String remoteAddr) {
    Instant now = Instant.now(clock);
    Window window =
        windows.compute(
            remoteAddr,
            (ip, existing) -> {
              if (existing == null || existing.expiresAt.isBefore(now)) {
                return new Window(now.plus(properties.ipWindowSeconds(), ChronoUnit.SECONDS), new AtomicInteger(0));
              }
              return existing;
            });
    return window.count.incrementAndGet() > properties.maxRequestsPerIpPerWindow();
  }

  private void writeTooManyRequests(HttpServletResponse response) throws IOException {
    response.setStatus(ErrorCode.TOO_MANY_AUTH_REQUESTS.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.TOO_MANY_AUTH_REQUESTS));
  }

  @Scheduled(fixedRate = 600_000)
  void evictStaleWindows() {
    Instant staleBefore =
        Instant.now(clock).minus(properties.ipWindowSeconds() * STALE_WINDOW_MULTIPLIER, ChronoUnit.SECONDS);
    windows.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(staleBefore));
  }

  private record Window(Instant expiresAt, AtomicInteger count) {}
}
