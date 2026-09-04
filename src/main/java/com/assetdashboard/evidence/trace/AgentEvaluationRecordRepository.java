package com.assetdashboard.evidence.trace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvaluationRecordRepository
    extends JpaRepository<AgentEvaluationRecord, Long> {

  Optional<AgentEvaluationRecord> findByRunId(Long runId);

  void deleteAllByRunIdIn(List<Long> runIds);
}
