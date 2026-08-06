package com.assetdashboard.domain.user.controller;

import com.assetdashboard.domain.user.dto.LoginRequest;
import com.assetdashboard.domain.user.dto.LoginResponse;
import com.assetdashboard.domain.user.dto.SignupRequest;
import com.assetdashboard.domain.user.dto.UserResponse;
import com.assetdashboard.domain.user.service.UserService;
import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API (PRD 4-1).
 *
 * <p>로그아웃 API 는 두지 않는다. JWT 는 서버가 상태를 저장하지 않으므로 클라이언트가 토큰을 삭제하는 것으로
 * 로그아웃을 처리한다.
 */
@Tag(name = "Auth", description = "회원가입 / 로그인")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;

  /**
   * 회원가입.
   *
   * @param request 이메일·비밀번호·닉네임
   * @return 생성된 사용자 정보 (201)
   */
  @Operation(summary = "회원가입", description = "비밀번호는 8자 이상. 인증 불필요.")
  @PostMapping("/signup")
  public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(userService.signup(request));
  }

  /**
   * 로그인해 액세스 토큰을 발급받는다.
   *
   * @param request 이메일·비밀번호
   * @return 액세스 토큰
   */
  @Operation(summary = "로그인", description = "성공 시 24시간짜리 JWT 를 발급한다. 인증 불필요.")
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(userService.login(request));
  }

  /**
   * 현재 로그인한 사용자 정보를 조회한다.
   *
   * @param userId 인증된 사용자 id (SecurityContext 에서 주입)
   * @return 사용자 정보
   */
  @Operation(summary = "내 정보 조회", description = "토큰이 유효한지 확인하는 용도로도 사용한다.")
  @GetMapping("/me")
  public ResponseEntity<UserResponse> me(@CurrentUserId Long userId) {
    return ResponseEntity.ok(userService.getMe(userId));
  }
}
