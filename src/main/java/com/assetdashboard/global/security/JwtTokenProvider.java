package com.assetdashboard.global.security;

import com.assetdashboard.global.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JWT 액세스 토큰의 발급과 검증을 담당한다.
 *
 * <p>토큰의 subject 에는 사용자 id 만 담는다. 권한이나 이메일 같은 부가 정보를 토큰에 넣으면 사용자 정보가 바뀌었을 때
 * 토큰이 낡은 사실을 들고 다니게 되므로, 식별자만 담고 나머지는 필요할 때 DB에서 읽는다.
 */
@Slf4j
@Component
public class JwtTokenProvider {

  private final SecretKey key;
  private final Duration expiration;

  /**
   * 설정값으로 서명 키와 만료 시간을 준비한다.
   *
   * @param properties JWT 설정
   */
  public JwtTokenProvider(JwtProperties properties) {
    this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    this.expiration = Duration.ofMinutes(properties.expirationMinutes());
  }

  /**
   * 사용자 id 로 액세스 토큰을 발급한다.
   *
   * @param userId 토큰 소유자의 id
   * @return 서명된 JWT 문자열
   */
  public String createToken(Long userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expiration)))
        .signWith(key)
        .compact();
  }

  /**
   * 토큰의 서명과 만료를 검증하고 사용자 id 를 꺼낸다.
   *
   * @param token 검증할 JWT 문자열
   * @return 유효하면 사용자 id, 위조·만료·형식 오류이면 {@code null}
   */
  public Long parseUserId(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      return Long.valueOf(claims.getSubject());
    } catch (JwtException | IllegalArgumentException e) {
      // 인증 실패는 정상적으로 발생할 수 있는 흐름이므로 예외를 전파하지 않고 null 로 신호한다.
      log.debug("[JWT] 유효하지 않은 토큰: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 토큰 만료까지 남은 시간을 초 단위로 반환한다.
   *
   * @return 만료 시간(초)
   */
  public long getExpiresInSeconds() {
    return expiration.toSeconds();
  }
}
