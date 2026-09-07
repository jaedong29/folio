package com.assetdashboard.evidence.evaluation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveEvaluationBatchCaseRepository
    extends JpaRepository<LiveEvaluationBatchCaseResult, Long> {

  List<LiveEvaluationBatchCaseResult> findAllByBatchJobIdOrderByIdAsc(Long batchJobId);

  boolean existsByBatchJobIdAndCaseId(Long batchJobId, String caseId);

  @Modifying
  @Query("delete from LiveEvaluationBatchCaseResult result where result.batchJobId in :batchJobIds")
  void deleteAllByBatchJobIdIn(@Param("batchJobIds") List<Long> batchJobIds);
}
