package com.assetdashboard.evidence.trend;

import com.assetdashboard.evidence.agent.GroundedToolResult;

/** 가격 방향 Tool의 응답 본문과 서버가 추출한 결정적 채점 근거. */
public record PriceTrendEvidenceToolResult(
    PriceTrendEvidenceResponse payload, GroundedToolResult grounding) {}
