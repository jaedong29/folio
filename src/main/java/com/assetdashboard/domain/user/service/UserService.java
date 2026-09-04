package com.assetdashboard.domain.user.service;

import com.assetdashboard.domain.user.dto.EmailAvailabilityResponse;
import com.assetdashboard.domain.user.dto.LoginRequest;
import com.assetdashboard.domain.user.dto.LoginResponse;
import com.assetdashboard.domain.user.dto.SignupRequest;
import com.assetdashboard.domain.user.dto.UserResponse;
import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입과 로그인을 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider tokenProvider;

  /**
   * 회원가입 전에 이메일 사용 가능 여부를 확인한다.
   *
   * <p>이 응답은 입력 편의를 위한 사전 확인이며, 확인 직후 다른 요청이 같은 이메일로 가입할 수 있으므로
   * {@link #signup(SignupRequest)}에서도 유니크 여부를 다시 검사한다.
   *
   * @param rawEmail 사용자가 입력한 이메일
   * @return 정규화된 이메일과 사용 가능 여부
   */
  public EmailAvailabilityResponse checkEmailAvailability(String rawEmail) {
    String email = rawEmail.trim().toLowerCase();
    boolean available = !userRepository.existsByEmail(email);
    return new EmailAvailabilityResponse(
        email, available, available ? "사용 가능한 이메일입니다." : "이미 사용 중인 이메일입니다.");
  }

  /**
   * 새 사용자를 등록한다.
   *
   * @param request 회원가입 요청
   * @return 생성된 사용자 정보
   * @throws BusinessException 이미 사용 중인 이메일인 경우 {@code DUPLICATE_EMAIL}
   */
  @Transactional
  public UserResponse signup(SignupRequest request) {
    String email = request.email().trim().toLowerCase();
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
    }
    User user =
        User.create(email, passwordEncoder.encode(request.password()), request.nickname().trim());
    try {
      return UserResponse.from(userRepository.saveAndFlush(user));
    } catch (DataIntegrityViolationException exception) {
      // 중복확인 직후 다른 요청이 먼저 가입한 경합도 이메일 중복으로 일관되게 응답한다.
      throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
    }
  }

  /**
   * 자격 증명을 검증하고 액세스 토큰을 발급한다.
   *
   * @param request 로그인 요청
   * @return 액세스 토큰을 담은 응답
   * @throws BusinessException 이메일이 없거나 비밀번호가 틀린 경우 {@code INVALID_CREDENTIALS}
   */
  public LoginResponse login(LoginRequest request) {
    String email = request.email().trim().toLowerCase();
    // 미가입 이메일과 비밀번호 불일치를 같은 에러로 응답해 계정 존재 여부가 새어나가지 않게 한다.
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      log.warn("[Login] 비밀번호 불일치 userId={}", user.getId());
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    return LoginResponse.of(
        tokenProvider.createToken(user.getId()),
        tokenProvider.getExpiresInSeconds(),
        user.getNickname());
  }

  /**
   * 사용자 id 로 정보를 조회한다.
   *
   * @param userId 조회할 사용자 id
   * @return 사용자 정보
   * @throws BusinessException 사용자가 없으면 {@code UNAUTHORIZED} (토큰은 유효하나 계정이 사라진 상태)
   */
  public UserResponse getMe(Long userId) {
    return userRepository
        .findById(userId)
        .map(UserResponse::from)
        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
  }
}
