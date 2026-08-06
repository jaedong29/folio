package com.assetdashboard.domain.user.repository;

import com.assetdashboard.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** User 영속성 접근. */
public interface UserRepository extends JpaRepository<User, Long> {

  /**
   * 이메일로 사용자를 조회한다.
   *
   * @param email 조회할 이메일
   * @return 존재하면 사용자, 없으면 빈 Optional
   */
  Optional<User> findByEmail(String email);

  /**
   * 이메일 사용 여부를 확인한다.
   *
   * @param email 확인할 이메일
   * @return 이미 가입된 이메일이면 {@code true}
   */
  boolean existsByEmail(String email);
}
