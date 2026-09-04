package com.assetdashboard.domain.transaction.repository;

import com.assetdashboard.domain.transaction.entity.IdempotencyKey;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

  Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

  @Transactional
  void deleteAllByUserId(Long userId);

  @Modifying
  @Transactional
  @Query("delete from IdempotencyKey k where k.createdAt < :cutoff")
  int deleteAllByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
