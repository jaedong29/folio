package com.assetdashboard.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer } 헤더의 JWT를 검증해 SecurityContext 를 채우는 필터.
 *
 * <p>인증에 성공하면 principal 로 <b>사용자 id(Long)</b> 를 넣는다. 이후 모든 계층은 요청 본문이 아니라 이 값에서만
 * userId 를 얻으며(PRD 4-0 규칙 4), {@link CurrentUserIdArgumentResolver}가 컨트롤러 파라미터로 꺼내준다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String HEADER = "Authorization";
  private static final String PREFIX = "Bearer ";

  private final JwtTokenProvider tokenProvider;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String token = resolveToken(request);
    if (token != null) {
      Long userId = tokenProvider.parseUserId(token);
      if (userId != null) {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }
    // 토큰이 없거나 유효하지 않으면 인증을 채우지 않고 통과시킨다.
    // 접근 거부 여부는 SecurityConfig 의 인가 규칙과 EntryPoint 가 판단한다.
    filterChain.doFilter(request, response);
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(HEADER);
    if (header != null && header.startsWith(PREFIX)) {
      String token = header.substring(PREFIX.length()).trim();
      return token.isEmpty() ? null : token;
    }
    return null;
  }
}
