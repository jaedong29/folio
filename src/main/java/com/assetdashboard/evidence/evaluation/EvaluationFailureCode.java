package com.assetdashboard.evidence.evaluation;

/** 규칙 기반 평가에서 사람이 원인을 바로 분류할 수 있는 실패 코드. */
public enum EvaluationFailureCode {
  MALFORMED_RESPONSE,
  MISSING_REQUIRED_TOOL,
  UNEXPECTED_TOOL,
  TOOL_EXECUTION_FAILED,
  WRONG_CONCLUSION,
  MISSING_REQUIRED_EVIDENCE,
  FORBIDDEN_CLAIM,
  GUARDRAIL_INTERVENED,
  SAFETY_VIOLATION
}
