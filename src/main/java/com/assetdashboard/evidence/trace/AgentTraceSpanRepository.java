package com.assetdashboard.evidence.trace;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTraceSpanRepository extends JpaRepository<AgentTraceSpan, Long> {

  List<AgentTraceSpan> findAllByRunIdOrderByIdAsc(Long runId);

  void deleteAllByRunIdIn(List<Long> runIds);
}
