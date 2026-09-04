package com.assetdashboard.evidence.evaluation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveEvaluationBatchCaseRepository
    extends JpaRepository<LiveEvaluationBatchCaseResult, Long> {

  List<LiveEvaluationBatchCaseResult> findAllByBatchJobIdOrderByIdAsc(Long batchJobId);

  boolean existsByBatchJobIdAndCaseId(Long batchJobId, String caseId);

  void deleteAllByBatchJobIdIn(List<Long> batchJobIds);
}
