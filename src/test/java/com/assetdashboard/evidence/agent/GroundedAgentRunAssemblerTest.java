package com.assetdashboard.evidence.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.trace.AgentRunResult;
import com.assetdashboard.evidence.trace.AgentRunStatus;
import com.assetdashboard.evidence.trace.AgentTokenUsage;
import com.assetdashboard.evidence.trace.AgentTraceStep;
import com.assetdashboard.evidence.trace.AgentTraceStepStatus;
import com.assetdashboard.evidence.trace.AgentTraceStepType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GroundedAgentRunAssemblerTest {

  private final GroundedAgentRunAssembler assembler =
      new GroundedAgentRunAssembler(new EvidenceConclusionPolicy());

  @Test
  void derivesFactsAndConclusionOnlyFromGroundedToolResults() {
    GroundedToolResult toolResult =
        new GroundedToolResult(
            "getAssetEvidence",
            EvidenceConclusion.UNAVAILABLE,
            Set.of("FX_MISSING", "exchangeRate=null", "valuationKrw=null"),
            List.of("asset:11"));

    AgentRunResult result =
        assembler.assemble(
            "550e8400-e29b-41d4-a716-446655440000",
            "financial-agent-v1",
            AgentRunStatus.COMPLETED,
            new AgentModelResponse(
                "현재 환율을 확인할 수 없습니다.",
                "not-connected-fixture",
                20,
                new AgentTokenUsage(100L, 20L)),
            traceSteps("getAssetEvidence"),
            List.of(toolResult),
            Set.of());

    assertThat(result.conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(result.evidenceFacts())
        .containsExactlyInAnyOrder("FX_MISSING", "exchangeRate=null", "valuationKrw=null");
  }

  @Test
  void rejectsCompletedTraceWithoutMatchingGroundedToolResult() {
    assertThatThrownBy(
            () ->
                assembler.assemble(
                    "550e8400-e29b-41d4-a716-446655440000",
                    "financial-agent-v1",
                    AgentRunStatus.COMPLETED,
                    new AgentModelResponse(
                        "답변",
                        "not-connected-fixture",
                        20,
                        AgentTokenUsage.unknown()),
                    traceSteps("getAssetEvidence"),
                    List.of(),
                    Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Tool Trace와 결정적 Tool 결과");
  }

  private List<AgentTraceStep> traceSteps(String toolName) {
    return List.of(
        new AgentTraceStep(
            "root",
            null,
            AgentTraceStepType.AGENT,
            "financialEvidenceAgent",
            AgentTraceStepStatus.SUCCESS,
            20,
            null,
            List.of()),
        new AgentTraceStep(
            "tool-1",
            "root",
            AgentTraceStepType.TOOL,
            toolName,
            AgentTraceStepStatus.SUCCESS,
            5,
            null,
            List.of("asset:11")));
  }
}
