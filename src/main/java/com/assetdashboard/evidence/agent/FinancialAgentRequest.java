package com.assetdashboard.evidence.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 자산 근거 Agent 질문. 자산과 사용자는 경로 및 인증 컨텍스트에서 결정한다. */
public record FinancialAgentRequest(
    @NotBlank @Size(max = 1_000) String question) {}
