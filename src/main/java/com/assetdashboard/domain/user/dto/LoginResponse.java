package com.assetdashboard.domain.user.dto;

/**
 * 로그인·재발급 응답.
 *
 * @param accessToken 이후 요청의 {@code Authorization: Bearer } 헤더에 넣을 JWT
 * @param tokenType 토큰 타입. 항상 {@code Bearer}
 * @param expiresIn Access Token 만료까지 남은 시간(초)
 * @param refreshToken 만료 전 갱신에 쓰는 Refresh Token. 매 재발급마다 값이 바뀐다(rotate)
 * @param nickname 화면 인사말에 쓰는 닉네임
 */
public record LoginResponse(
    String accessToken, String tokenType, long expiresIn, String refreshToken, String nickname) {

  /**
   * Bearer 타입 로그인 응답을 만든다.
   *
   * @param accessToken 발급된 JWT
   * @param expiresIn Access Token 만료까지 남은 시간(초)
   * @param refreshToken 발급된 Refresh Token
   * @param nickname 사용자 닉네임
   * @return 로그인 응답
   */
  public static LoginResponse of(
      String accessToken, long expiresIn, String refreshToken, String nickname) {
    return new LoginResponse(accessToken, "Bearer", expiresIn, refreshToken, nickname);
  }
}
