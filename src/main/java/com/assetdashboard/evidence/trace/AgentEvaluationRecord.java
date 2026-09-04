package com.assetdashboard.evidence.trace;

import com.assetdashboard.evidence.evaluation.FinancialEvidenceEvaluationResult;
import com.assetdashboard.global.common.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Agent 실행을 골든셋과 비교한 규칙 기반 평가 기록. */
@Getter
@Entity
@Table(
    name = "ai_evaluation_results",
    uniqueConstraints = @UniqueConstraint(name = "uk_ai_eval_run", columnNames = "run_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentEvaluationRecord extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "run_id", nullable = false)
  private Long runId;

  @Column(name = "case_id", nullable = false, length = 100)
  private String caseId;

  @Column(name = "scorer_version", nullable = false, length = 40)
  private String scorerVersion;

  @Column(nullable = false)
  private boolean passed;

  @Column(name = "hard_failure", nullable = false)
  private boolean hardFailure;

  @Column(name = "failure_codes", length = 1000)
  private String failureCodes;

  @Column(name = "failure_details", length = 4000)
  private String failureDetails;

  private AgentEvaluationRecord(
      Long runId,
      String caseId,
      String scorerVersion,
      boolean passed,
      boolean hardFailure,
      String failureCodes,
      String failureDetails) {
    this.runId = runId;
    this.caseId = caseId;
    this.scorerVersion = scorerVersion;
    this.passed = passed;
    this.hardFailure = hardFailure;
    this.failureCodes = failureCodes;
    this.failureDetails = failureDetails;
  }

  public static AgentEvaluationRecord create(
      Long runId, FinancialEvidenceEvaluationResult result) {
    String codes =
        result.failureCodes().isEmpty()
            ? null
            : result.failureCodes().stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(","));
    String details = result.failureDetails().isEmpty() ? null : String.join("\n", result.failureDetails());
    return new AgentEvaluationRecord(
        runId,
        result.caseId(),
        result.scorerVersion(),
        result.passed(),
        result.hardFailure(),
        codes,
        details);
  }
}
