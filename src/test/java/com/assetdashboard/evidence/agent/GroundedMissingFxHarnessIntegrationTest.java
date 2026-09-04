package com.assetdashboard.evidence.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.evaluation.FinancialEvidenceEvaluationHarness;
import com.assetdashboard.evidence.tool.AssetEvidenceToolAdapter;
import com.assetdashboard.evidence.tool.AssetEvidenceToolResult;
import com.assetdashboard.evidence.trace.AgentRunResult;
import com.assetdashboard.evidence.trace.AgentRunStatus;
import com.assetdashboard.evidence.trace.AgentTokenUsage;
import com.assetdashboard.evidence.trace.AgentTraceResponse;
import com.assetdashboard.evidence.trace.AgentTraceStep;
import com.assetdashboard.evidence.trace.AgentTraceStepStatus;
import com.assetdashboard.evidence.trace.AgentTraceStepType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 실제 DB 자산부터 Tool Adapter·결정적 조립·골든셋 채점·Trace 저장까지 관통한다. */
@SpringBootTest
@Transactional
class GroundedMissingFxHarnessIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private AssetEvidenceToolAdapter assetEvidenceToolAdapter;
  @Autowired private GroundedAgentRunAssembler assembler;
  @Autowired private FinancialEvidenceEvaluationHarness harness;

  @Test
  void mapsMissingFxFromTheRealToolResponseInsteadOfModelText() {
    User user =
        userRepository.save(
            User.create("grounded-missing-fx@example.com", "encoded", "grounded"));
    Asset asset = Asset.create(user.getId(), AssetType.CRYPTO, "BTC", "비트코인", "USDT");
    asset.initializePosition(
        BigDecimal.ONE, new BigDecimal("60000"), new BigDecimal("1400"));
    asset.updateCurrentPrice(new BigDecimal("65000"), AssetSource.API);
    Asset savedAsset = assetRepository.save(asset);

    AssetEvidenceToolResult toolResult =
        assetEvidenceToolAdapter.execute(user.getId(), savedAsset.getId());

    assertThat(toolResult.payload().exchangeRateEvidence().value()).isNull();
    assertThat(toolResult.grounding().conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(toolResult.grounding().evidenceFacts())
        .contains("FX_MISSING", "exchangeRate=null", "valuationKrw=null");

    String traceId = UUID.randomUUID().toString();
    AgentRunResult runResult =
        assembler.assemble(
            traceId,
            "financial-agent-v1",
            AgentRunStatus.COMPLETED,
            new AgentModelResponse(
                "현재 환율 근거가 없어 원화 평가금액은 확인 불가입니다.",
                "not-connected-fixture",
                35,
                new AgentTokenUsage(120L, 32L)),
            traceSteps(toolResult),
            List.of(toolResult.grounding()),
            Set.of());

    AgentTraceResponse trace =
        harness.evaluateAndRecord(user.getId(), "missing-fx", runResult);

    assertThat(runResult.conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(runResult.evidenceFacts())
        .contains("FX_MISSING", "exchangeRate=null", "valuationKrw=null");
    assertThat(trace.evaluation().passed()).isTrue();
    assertThat(trace.rawQuestionStored()).isFalse();
    assertThat(trace.rawAnswerStored()).isFalse();
  }

  private List<AgentTraceStep> traceSteps(AssetEvidenceToolResult toolResult) {
    return List.of(
        new AgentTraceStep(
            "root",
            null,
            AgentTraceStepType.AGENT,
            "financialEvidenceAgent",
            AgentTraceStepStatus.SUCCESS,
            35,
            null,
            List.of()),
        new AgentTraceStep(
            "model-plan",
            "root",
            AgentTraceStepType.MODEL,
            "selectTool",
            AgentTraceStepStatus.SUCCESS,
            8,
            null,
            List.of()),
        new AgentTraceStep(
            "tool-asset",
            "model-plan",
            AgentTraceStepType.TOOL,
            AssetEvidenceToolAdapter.TOOL_NAME,
            AgentTraceStepStatus.SUCCESS,
            9,
            null,
            toolResult.grounding().referenceIds()),
        new AgentTraceStep(
            "guardrail",
            "tool-asset",
            AgentTraceStepType.GUARDRAIL,
            "financialSafetyPolicy",
            AgentTraceStepStatus.SUCCESS,
            2,
            null,
            List.of()),
        new AgentTraceStep(
            "model-answer",
            "guardrail",
            AgentTraceStepType.MODEL,
            "composeGroundedAnswer",
            AgentTraceStepStatus.SUCCESS,
            16,
            null,
            List.of()));
  }
}
