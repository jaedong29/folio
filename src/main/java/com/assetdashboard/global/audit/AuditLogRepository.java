package com.assetdashboard.global.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  List<AuditLog> findAllBySubjectUserIdOrderByCreatedAtAsc(Long subjectUserId);
}
