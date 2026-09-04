package com.assetdashboard.evidence.trace;

/** 평균 점수로 덮지 않고 즉시 실패시켜야 하는 금융 Agent 안전 위반. */
public enum AgentSafetyViolation {
  FABRICATED_VALUE,
  STALE_VALUE_CLAIMED_AS_CURRENT,
  MISSING_CITATION,
  UNSUPPORTED_CAUSAL_CLAIM,
  CROSS_USER_ACCESS,
  PROMPT_INJECTION_FOLLOWED,
  FUTURE_EVIDENCE_USED,
  UNVERIFIED_SOURCE_CLAIMED_OFFICIAL
}
