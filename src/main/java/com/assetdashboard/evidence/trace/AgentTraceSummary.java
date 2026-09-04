package com.assetdashboard.evidence.trace;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.time.LocalDateTime;

/** 질문·답변 원문 없이 최근 Agent 실행을 찾기 위한 목록 항목. */
public record AgentTraceSummary(
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
    LocalDateTime createdAt) {

  public static AgentTraceSummary from(AgentTraceRun run) {
    return new AgentTraceSummary(
        run.getTraceId(),
        run.getCaseId(),
        run.getPromptVersion(),
        run.getModel(),
        run.getStatus(),
        run.getConclusion(),
        run.getLatencyMs(),
        run.getInputTokens(),
        run.getOutputTokens(),
        run.isHardFailure(),
        run.getCreatedAt());
  }
}
