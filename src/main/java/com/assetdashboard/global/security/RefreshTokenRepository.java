package com.assetdashboard.global.security;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :revokedAt "
          + "where t.familyId = :familyId and t.revokedAt is null")
  int revokeAllByFamilyId(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt);

  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :revokedAt "
          + "where t.userId = :userId and t.revokedAt is null")
  int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

  @Transactional
  void deleteAllByUserId(Long userId);

  @Modifying
  @Query("delete from RefreshToken t where t.familyId in :familyIds")
  int deleteAllByFamilyIdIn(@Param("familyIds") List<String> familyIds);
}
