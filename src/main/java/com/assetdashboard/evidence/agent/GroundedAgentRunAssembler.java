package com.assetdashboard.evidence.agent;

import com.assetdashboard.evidence.trace.AgentRunResult;
import com.assetdashboard.evidence.trace.AgentRunStatus;
import com.assetdashboard.evidence.trace.AgentSafetyViolation;
import com.assetdashboard.evidence.trace.AgentTraceStep;
import com.assetdashboard.evidence.trace.AgentTraceStepType;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 모델 답변과 애플리케이션이 추출한 Tool 근거를 섞지 않고 최종 실행 결과로 조립한다. */
@Component
@RequiredArgsConstructor
public class GroundedAgentRunAssembler {

  private final EvidenceConclusionPolicy conclusionPolicy;

  public AgentRunResult assemble(
      String traceId,
      String promptVersion,
      AgentRunStatus status,
      AgentModelResponse modelResponse,
      List<AgentTraceStep> steps,
      List<GroundedToolResult> toolResults,
      Set<AgentSafetyViolation> safetyViolations) {
    List<GroundedToolResult> safeToolResults =
        toolResults == null ? List.of() : List.copyOf(toolResults);
    validateCompletedToolResults(status, steps, safeToolResults);

    Set<String> evidenceFacts =
        safeToolResults.stream()
            .map(GroundedToolResult::evidenceFacts)
            .flatMap(Collection::stream)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    return new AgentRunResult(
        traceId,
        promptVersion,
        modelResponse.model(),
        status,
        conclusionPolicy.combine(safeToolResults),
        modelResponse.finalAnswer(),
        steps,
        evidenceFacts,
        safetyViolations,
        modelResponse.latencyMs(),
        modelResponse.tokenUsage());
  }

  private void validateCompletedToolResults(
      AgentRunStatus status,
      List<AgentTraceStep> steps,
      List<GroundedToolResult> toolResults) {
    if (status != AgentRunStatus.COMPLETED) {
      return;
    }
    Set<String> tracedTools =
        steps.stream()
            .filter(step -> step.type() == AgentTraceStepType.TOOL)
            .map(AgentTraceStep::name)
            .collect(Collectors.toSet());
    Set<String> groundedTools =
        toolResults.stream().map(GroundedToolResult::toolName).collect(Collectors.toSet());
    if (!tracedTools.equals(groundedTools)) {
      throw new IllegalArgumentException(
          "완료된 Tool Trace와 결정적 Tool 결과가 일치해야 합니다. traced=%s, grounded=%s"
              .formatted(tracedTools, groundedTools));
    }
  }
}
