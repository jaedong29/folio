package com.assetdashboard.global.audit;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  List<AuditLog> findAllBySubjectUserIdOrderByCreatedAtAsc(Long subjectUserId);

  @Query("select log.id from AuditLog log where log.createdAt < :cutoff")
  List<Long> findIdsByCreatedAtBefore(LocalDateTime cutoff, Pageable pageable);
}
