package com.assetdashboard.domain.user.entity;

import com.assetdashboard.global.common.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인증의 주체이자 Asset 의 소유자.
 *
 * <p>Asset 을 컬렉션으로 들고 있지 않다. Asset 이 별도의 Aggregate Root 이므로 User 는 자산 상태에 대한 어떤
 * 책임도 지지 않으며, 자산 1건을 바꾸려고 User 전체를 로드하는 일이 생기지 않게 한다(PRD 2장).
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  /** BCrypt 로 해시된 비밀번호. 평문은 어떤 경우에도 저장하지 않는다. */
  @Column(nullable = false, length = 255)
  private String password;

  @Column(nullable = false, length = 50)
  private String nickname;

  private User(String email, String encodedPassword, String nickname) {
    this.email = email;
    this.password = encodedPassword;
    this.nickname = nickname;
  }

  /**
   * 새 사용자를 생성한다.
   *
   * @param email 로그인 식별자로 사용할 이메일
   * @param encodedPassword <b>이미 해시된</b> 비밀번호
   * @param nickname 표시용 닉네임
   * @return 생성된 사용자
   */
  public static User create(String email, String encodedPassword, String nickname) {
    return new User(email, encodedPassword, nickname);
  }

  /** 비밀번호 해시를 새 값으로 교체한다.
   *
   * @param encodedPassword 이미 해시된 새 비밀번호
   */
  public void changePassword(String encodedPassword) {
    this.password = encodedPassword;
  }
}
