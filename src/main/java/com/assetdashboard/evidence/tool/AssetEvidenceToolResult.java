package com.assetdashboard.evidence.tool;

import com.assetdashboard.evidence.agent.GroundedToolResult;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse;

/** 모델에 제공할 계산 Evidence 원문과 채점에 사용할 결정적 근거를 함께 반환한다. */
public record AssetEvidenceToolResult(
    AssetEvidenceResponse payload, GroundedToolResult grounding) {}
