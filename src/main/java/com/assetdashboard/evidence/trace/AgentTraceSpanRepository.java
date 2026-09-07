package com.assetdashboard.evidence.trace;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentTraceSpanRepository extends JpaRepository<AgentTraceSpan, Long> {

  List<AgentTraceSpan> findAllByRunIdOrderByIdAsc(Long runId);

  @Modifying
  @Query("delete from AgentTraceSpan span where span.runId in :runIds")
  void deleteAllByRunIdIn(@Param("runIds") List<Long> runIds);
}
