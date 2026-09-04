package com.assetdashboard.evidence.document;

import com.assetdashboard.evidence.agent.GroundedToolResult;

/** symbol 근거 Tool의 응답 본문과 서버가 추출한 결정적 채점 근거. */
public record SymbolEvidenceToolResult(SymbolEvidenceResponse payload, GroundedToolResult grounding) {}
