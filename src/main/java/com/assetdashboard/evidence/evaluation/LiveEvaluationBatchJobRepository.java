package com.assetdashboard.evidence.evaluation;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveEvaluationBatchJobRepository
    extends JpaRepository<LiveEvaluationBatchJob, Long> {

  Optional<LiveEvaluationBatchJob> findByBatchIdAndUserId(String batchId, Long userId);

  List<LiveEvaluationBatchJob> findAllByUserId(Long userId);

  void deleteAllByUserId(Long userId);

  List<LiveEvaluationBatchJob> findAllByStatus(LiveEvaluationBatchStatus status);

  Optional<LiveEvaluationBatchJob> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
      Long userId, Collection<LiveEvaluationBatchStatus> statuses);

  @Query(
      "select j.id from LiveEvaluationBatchJob j "
          + "where j.status in :statuses and j.completedAt < :cutoff order by j.id")
  List<Long> findIdsByStatusInAndCompletedAtBefore(
      @Param("statuses") Collection<LiveEvaluationBatchStatus> statuses,
      @Param("cutoff") Instant cutoff,
      Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from LiveEvaluationBatchJob j where j.status = :status order by j.createdAt asc")
  List<LiveEvaluationBatchJob> findNextForUpdate(
      @Param("status") LiveEvaluationBatchStatus status, Pageable pageable);
}
