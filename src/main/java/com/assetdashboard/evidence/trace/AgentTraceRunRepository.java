package com.assetdashboard.evidence.trace;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AgentTraceRunRepository extends JpaRepository<AgentTraceRun, Long> {

  boolean existsByTraceId(String traceId);

  Optional<AgentTraceRun> findByTraceIdAndUserId(String traceId, Long userId);

  List<AgentTraceRun> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  @Query("select run.id from AgentTraceRun run where run.userId = :userId")
  List<Long> findIdsByUserId(Long userId);

  @Query(
      "select run.id from AgentTraceRun run "
          + "where run.createdAt < :cutoff "
          + "and not exists (select batchCase.id from LiveEvaluationBatchCaseResult batchCase "
          + "where batchCase.traceId = run.traceId) "
          + "order by run.id")
  List<Long> findIdsByCreatedAtBefore(LocalDateTime cutoff, Pageable pageable);

  void deleteAllByUserId(Long userId);
}
