package com.assetdashboard.evidence.agent;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;

/** 모델 제공자와 Agent 오케스트레이션 사이의 포트. */
public interface FinancialAgentModelClient {

  AgentToolCallResponse requestTool(String question, Long assetId, String requiredToolName);

  AgentModelResponse composeGroundedAnswer(
      String question,
      AgentToolCallResponse toolCall,
      Object evidence,
      EvidenceConclusion answerConclusion);
}
