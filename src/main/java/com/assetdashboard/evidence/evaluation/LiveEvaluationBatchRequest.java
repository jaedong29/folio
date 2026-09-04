package com.assetdashboard.evidence.evaluation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record LiveEvaluationBatchRequest(
    @NotNull(message = "실제 NIM 호출 확인값은 필수입니다.") Boolean confirmLiveCalls,
    @Size(max = 5, message = "한 번에 실행할 수 있는 실제 NIM 평가는 최대 5건입니다.")
        List<String> caseIds) {

  public LiveEvaluationBatchRequest {
    caseIds = caseIds == null ? List.of() : List.copyOf(caseIds);
  }
}
