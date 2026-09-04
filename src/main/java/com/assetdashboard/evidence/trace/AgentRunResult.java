package com.assetdashboard.evidence.trace;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * LLM 제공자와 무관한 한 번의 Agent 실행 결과.
 *
 * <p>최종 답변은 실행 중 채점에만 사용하고 영속 Trace에는 해시만 저장한다. 운영 경로에서는
 * {@code GroundedAgentRunAssembler}가 Tool 결과에서 conclusion과 evidenceFacts를 만들며 모델 응답에서
 * 이 필드를 받지 않는다.
 */
public record AgentRunResult(
    String traceId,
    String promptVersion,
    String model,
    AgentRunStatus status,
    EvidenceConclusion conclusion,
    String finalAnswer,
    List<AgentTraceStep> steps,
    Set<String> evidenceFacts,
    Set<AgentSafetyViolation> safetyViolations,
    long latencyMs,
    AgentTokenUsage tokenUsage) {

  public AgentRunResult {
    if (traceId == null || traceId.isBlank()) {
      throw new IllegalArgumentException("traceId가 필요합니다.");
    }
    if (promptVersion == null || promptVersion.isBlank()) {
      throw new IllegalArgumentException("promptVersion이 필요합니다.");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model이 필요합니다.");
    }
    Objects.requireNonNull(status, "status가 필요합니다.");
    if (latencyMs < 0) {
      throw new IllegalArgumentException("latencyMs는 음수일 수 없습니다.");
    }
    steps = steps == null ? List.of() : List.copyOf(steps);
    evidenceFacts = evidenceFacts == null ? Set.of() : Set.copyOf(evidenceFacts);
    safetyViolations = safetyViolations == null ? Set.of() : Set.copyOf(safetyViolations);
    tokenUsage = tokenUsage == null ? AgentTokenUsage.unknown() : tokenUsage;
  }
}
