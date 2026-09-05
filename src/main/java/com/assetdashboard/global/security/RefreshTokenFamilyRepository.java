package com.assetdashboard.global.security;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenFamilyRepository
    extends JpaRepository<RefreshTokenFamily, String> {

  List<RefreshTokenFamily> findAllByUserId(Long userId);

  @Modifying
  @Query(
      "update RefreshTokenFamily f set f.revokedAt = :revokedAt "
          + "where f.userId = :userId and f.revokedAt is null")
  int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

  @Query("select f.familyId from RefreshTokenFamily f where f.expiresAt < :cutoff")
  List<String> findIdsByExpiresAtBefore(@Param("cutoff") Instant cutoff);

  @Modifying
  @Query("delete from RefreshTokenFamily f where f.familyId in :familyIds")
  int deleteAllByFamilyIdIn(@Param("familyIds") List<String> familyIds);

  @Transactional
  void deleteAllByUserId(Long userId);
}
