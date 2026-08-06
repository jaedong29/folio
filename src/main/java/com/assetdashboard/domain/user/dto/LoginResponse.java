package com.assetdashboard.domain.user.dto;

/**
 * 로그인 응답 (PRD 4-1).
 *
 * @param accessToken 이후 요청의 {@code Authorization: Bearer } 헤더에 넣을 JWT
 * @param tokenType 토큰 타입. 항상 {@code Bearer}
 * @param expiresIn 만료까지 남은 시간(초)
 * @param nickname 화면 인사말에 쓰는 닉네임
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, String nickname) {

  /**
   * Bearer 타입 로그인 응답을 만든다.
   *
   * @param accessToken 발급된 JWT
   * @param expiresIn 만료까지 남은 시간(초)
   * @param nickname 사용자 닉네임
   * @return 로그인 응답
   */
  public static LoginResponse of(String accessToken, long expiresIn, String nickname) {
    return new LoginResponse(accessToken, "Bearer", expiresIn, nickname);
  }
}
