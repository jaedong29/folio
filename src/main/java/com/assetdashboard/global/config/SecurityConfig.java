package com.assetdashboard.global.config;

import com.assetdashboard.global.security.JwtAuthenticationFilter;
import com.assetdashboard.global.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 인증 필터 체인과 공개 경로를 정의한다.
 *
 * <p>기본 정책은 <b>모두 인증 필요</b>이며, 회원가입·로그인·문서·정적 리소스만 예외로 연다. 새 API 를 추가할 때
 * 별도 조치를 하지 않으면 자동으로 보호되도록 하기 위함이다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

  private static final String[] PUBLIC_ENDPOINTS = {
    "/api/auth/signup",
    "/api/auth/login",
    "/api/auth/email-availability",
    "/actuator/health",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/h2-console/**"
  };

  private static final String[] STATIC_RESOURCES = {
    "/", "/index.html", "/login.html", "/css/**", "/js/**", "/data/**", "/favicon.ico"
  };

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RestAuthenticationEntryPoint authenticationEntryPoint;

  /**
   * 비밀번호 해시 인코더를 등록한다.
   *
   * @return BCrypt 인코더
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * JWT 기반 무상태 보안 필터 체인을 구성한다.
   *
   * @param http 보안 설정 빌더
   * @return 구성된 필터 체인
   * @throws Exception 설정 중 오류가 발생한 경우
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        // 토큰 기반이므로 세션을 만들지 않는다. 로그아웃도 클라이언트의 토큰 삭제로 처리한다(PRD 4-1).
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        // H2 콘솔이 frame 을 쓰므로 same-origin 만 허용한다.
        .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(STATIC_RESOURCES)
                    .permitAll()
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
