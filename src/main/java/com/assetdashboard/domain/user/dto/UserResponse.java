package com.assetdashboard.domain.user.dto;

import com.assetdashboard.domain.user.entity.User;
import java.time.LocalDateTime;

/**
 * 사용자 정보 응답. 비밀번호 해시는 어떤 경우에도 포함하지 않는다.
 *
 * @param id 사용자 id
 * @param email 이메일
 * @param nickname 닉네임
 * @param createdAt 가입 시각
 */
public record UserResponse(Long id, String email, String nickname, LocalDateTime createdAt) {

  /**
   * 엔티티를 응답 DTO 로 변환한다.
   *
   * @param user 변환할 사용자 엔티티
   * @return 사용자 응답
   */
  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getCreatedAt());
  }
}
