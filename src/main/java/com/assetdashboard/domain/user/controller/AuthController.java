package com.assetdashboard.domain.user.controller;

import com.assetdashboard.domain.user.dto.EmailAvailabilityResponse;
import com.assetdashboard.domain.user.dto.ChangePasswordRequest;
import com.assetdashboard.domain.user.dto.DeleteAccountRequest;
import com.assetdashboard.domain.user.dto.LoginRequest;
import com.assetdashboard.domain.user.dto.LoginResponse;
import com.assetdashboard.domain.user.dto.RefreshTokenRequest;
import com.assetdashboard.domain.user.dto.SignupRequest;
import com.assetdashboard.domain.user.dto.UserResponse;
import com.assetdashboard.domain.user.service.UserService;
import com.assetdashboard.domain.user.service.UserAccountService;
import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API (PRD 4-1).
 *
 * <p>Access Token은 짧게 살고(기본 30분) 무상태로 검증한다. 세션 연장은 서버가 저장·회전·폐기할 수 있는
 * Refresh Token이 담당하며, 로그아웃과 비밀번호 변경·회원 탈퇴는 이 Refresh Token을 실제로 무효화한다.
 */
@Tag(name = "Auth", description = "회원가입 / 로그인")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

  private final UserService userService;
  private final UserAccountService userAccountService;

  /**
   * 회원가입 이메일 중복 여부를 확인한다.
   *
   * @param email 확인할 이메일
   * @return 사용 가능 여부
   */
  @Operation(summary = "이메일 중복확인", description = "회원가입 편의를 위한 사전 확인이며 가입 요청에서 다시 검증한다.")
  @GetMapping("/email-availability")
  public ResponseEntity<EmailAvailabilityResponse> checkEmailAvailability(
      @RequestParam
          @NotBlank(message = "이메일은 필수입니다.")
          @Email(message = "이메일 형식이 올바르지 않습니다.")
          String email) {
    return ResponseEntity.ok(userService.checkEmailAvailability(email));
  }

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
  @Operation(
      summary = "로그인",
      description = "성공 시 짧은 수명의 Access Token과 세션 연장용 Refresh Token을 함께 발급한다. 인증 불필요.")
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(userService.login(request));
  }

  /**
   * Refresh Token을 새 Access Token·Refresh Token으로 교환한다.
   *
   * @param request 이전에 받은 Refresh Token
   * @return 새로 발급된 토큰 쌍
   */
  @Operation(
      summary = "토큰 재발급",
      description = "Refresh Token은 매 호출마다 새 값으로 교체(rotate)된다. 이미 교체돼 폐기된 토큰이 다시 오면 "
          + "탈취로 간주해 같은 로그인에서 나온 모든 Refresh Token을 폐기한다. 인증 불필요.")
  @PostMapping("/refresh")
  public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(userService.refresh(request.refreshToken()));
  }

  /**
   * 이 기기의 로그인 family를 폐기한다.
   *
   * @param request 폐기할 Refresh Token
   * @return 본문이 없는 성공 응답
   */
  @Operation(
      summary = "로그아웃",
      description = "제시한 Refresh Token이 속한 로그인 family를 서버에서 폐기한다. 이미 발급된 Access Token은 "
          + "자체 만료 시각까지 유효하므로 만료 시간을 짧게 유지한다. 인증 불필요(Refresh Token 자체가 자격 증명).")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
    userService.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
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

  /** 현재 비밀번호를 확인하고 새 비밀번호를 저장한다.
   *
   * @param userId 인증된 사용자 id
   * @param request 현재 비밀번호와 새 비밀번호
   * @return 본문이 없는 성공 응답
   */
  @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인한 뒤 새 비밀번호를 저장한다.")
  @PatchMapping("/password")
  public ResponseEntity<Void> changePassword(
      @CurrentUserId Long userId, @Valid @RequestBody ChangePasswordRequest request) {
    userAccountService.changePassword(userId, request);
    return ResponseEntity.noContent().build();
  }

  /** 계정과 연결된 자산·거래·Snapshot을 영구 삭제한다.
   *
   * @param userId 인증된 사용자 id
   * @param request 현재 비밀번호와 DELETE 확인 문구
   * @return 본문이 없는 성공 응답
   */
  @Operation(summary = "회원 탈퇴", description = "현재 계정과 연결된 모든 데이터를 영구 삭제한다.")
  @DeleteMapping("/account")
  public ResponseEntity<Void> deleteAccount(
      @CurrentUserId Long userId, @Valid @RequestBody DeleteAccountRequest request) {
    userAccountService.deleteAccount(userId, request);
    return ResponseEntity.noContent().build();
  }
}
