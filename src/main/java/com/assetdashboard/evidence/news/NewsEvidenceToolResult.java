package com.assetdashboard.evidence.news;

import com.assetdashboard.evidence.agent.GroundedToolResult;

public record NewsEvidenceToolResult(
    NewsEvidenceResponse payload, GroundedToolResult grounding) {}
