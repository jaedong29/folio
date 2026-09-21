package com.assetdashboard.connection;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface AccountConnectionRepository extends JpaRepository<AccountConnection, Long> {
  List<AccountConnection> findAllByUserIdOrderById(Long userId);
  Optional<AccountConnection> findByUserIdAndProvider(Long userId, ConnectionProvider provider);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from AccountConnection c where c.id = :id")
  Optional<AccountConnection> findLockedById(Long id);
  @Query("select c.id from AccountConnection c where c.nextSyncAt <= :now order by c.nextSyncAt, c.id")
  List<Long> findDueIds(Instant now, Pageable pageable);
  @Modifying
  @org.springframework.transaction.annotation.Transactional
  @Query("delete from AccountConnection c where c.userId = :userId")
  void deleteAllByUserId(Long userId);
}
