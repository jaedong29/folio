package com.assetdashboard.evidence.trace;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.time.LocalDateTime;
import java.util.List;

/** 민감한 원문을 제외하고 사용자에게 보여주는 Agent Trace 트리. */
public record AgentTraceResponse(
    String traceId,
    String caseId,
    String promptVersion,
    String model,
    AgentRunStatus status,
    EvidenceConclusion conclusion,
    long latencyMs,
    Long inputTokens,
    Long outputTokens,
    boolean hardFailure,
    boolean rawQuestionStored,
    boolean rawAnswerStored,
    LocalDateTime createdAt,
    List<AgentTraceNode> steps,
    EvaluationSummary evaluation) {

  public record AgentTraceNode(
      String spanId,
      AgentTraceStepType type,
      String name,
      AgentTraceStepStatus status,
      long latencyMs,
      String errorCode,
      List<String> referenceIds,
      List<AgentTraceNode> children) {}

  public record EvaluationSummary(
      String caseId,
      String scorerVersion,
      boolean passed,
      boolean hardFailure,
      List<String> failureCodes,
      List<String> failureDetails) {}
}
