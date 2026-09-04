package com.assetdashboard.evidence.trace;

/** Trace 트리에서 관찰 가능한 단계 유형. 모델의 비공개 추론 과정은 기록하지 않는다. */
public enum AgentTraceStepType {
  AGENT,
  ROUTER,
  MODEL,
  TOOL,
  RETRIEVAL,
  GUARDRAIL,
  EVALUATION
}
