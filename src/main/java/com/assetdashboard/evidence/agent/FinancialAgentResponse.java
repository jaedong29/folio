package com.assetdashboard.evidence.agent;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.util.List;

/** 원문을 영속하지 않고 현재 요청에만 반환하는 금융 Agent 답변. */
public record FinancialAgentResponse(
    String traceId,
    EvidenceConclusion conclusion,
    String answer,
    String model,
    long latencyMs,
    Long inputTokens,
    Long outputTokens,
    List<String> evidenceReferenceIds,
    FinancialQuestionIntent intent,
    List<String> toolsUsed) {}
