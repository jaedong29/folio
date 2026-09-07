package com.assetdashboard.evidence.trace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentEvaluationRecordRepository
    extends JpaRepository<AgentEvaluationRecord, Long> {

  Optional<AgentEvaluationRecord> findByRunId(Long runId);

  @Modifying
  @Query("delete from AgentEvaluationRecord result where result.runId in :runIds")
  void deleteAllByRunIdIn(@Param("runIds") List<Long> runIds);
}
