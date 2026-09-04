package com.assetdashboard.evidence.evaluation;

import java.util.List;

public record ClaimedLiveEvaluationBatch(
    Long id, String batchId, Long userId, List<String> caseIds) {

  public ClaimedLiveEvaluationBatch {
    caseIds = List.copyOf(caseIds);
  }
}
