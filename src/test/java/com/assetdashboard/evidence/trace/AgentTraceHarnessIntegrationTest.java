package com.assetdashboard.evidence.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.evaluation.FinancialEvidenceEvaluationHarness;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AgentTraceHarnessIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private AgentTraceRunRepository runRepository;
  @Autowired private FinancialEvidenceEvaluationHarness harness;
  @Autowired private AgentTraceService traceService;

  @Test
  void evaluatesMissingFxAndStoresAUserOwnedTraceTreeWithoutRawContent() {
    User user =
        userRepository.save(
            User.create("trace-owner@example.com", "encoded-password", "trace-owner"));
    String traceId = UUID.randomUUID().toString();

    AgentTraceResponse response =
        harness.evaluateAndRecord(user.getId(), "missing-fx", missingFxRun(traceId));

    assertThat(response.traceId()).isEqualTo(traceId);
    assertThat(response.rawQuestionStored()).isFalse();
    assertThat(response.rawAnswerStored()).isFalse();
    assertThat(response.evaluation().passed()).isTrue();
    assertThat(response.steps()).singleElement().satisfies(root -> {
      assertThat(root.type()).isEqualTo(AgentTraceStepType.AGENT);
      assertThat(root.children()).hasSize(2);
      AgentTraceResponse.AgentTraceNode model =
          root.children().stream()
              .filter(child -> child.type() == AgentTraceStepType.MODEL)
              .findFirst()
              .orElseThrow();
      assertThat(model.children()).singleElement().satisfies(tool -> {
        assertThat(tool.name()).isEqualTo("getAssetEvidence");
        assertThat(tool.referenceIds()).containsExactly("asset:11", "evidence-trace:abc");
      });
      assertThat(root.children())
          .filteredOn(child -> child.type() == AgentTraceStepType.EVALUATION)
          .singleElement()
          .satisfies(evaluation -> assertThat(evaluation.name()).isEqualTo("rule-v1"));
    });

    AgentTraceRun persisted =
        runRepository.findByTraceIdAndUserId(traceId, user.getId()).orElseThrow();
    assertThat(persisted.getQuestionHash()).hasSize(64).doesNotContain("환율");
    assertThat(persisted.getAnswerHash()).hasSize(64).doesNotContain("평가금액");

    assertThat(traceService.getRecent(user.getId(), 20))
        .extracting(AgentTraceSummary::traceId)
        .containsExactly(traceId);
  }

  @Test
  void hidesAnotherUsersTraceAsNotFound() {
    User owner =
        userRepository.save(User.create("trace-owner2@example.com", "encoded", "owner"));
    User other =
        userRepository.save(User.create("trace-other@example.com", "encoded", "other"));
    String traceId = UUID.randomUUID().toString();
    harness.evaluateAndRecord(owner.getId(), "missing-fx", missingFxRun(traceId));

    assertThatThrownBy(() -> traceService.getTrace(other.getId(), traceId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AGENT_TRACE_NOT_FOUND));
  }

  @Test
  void rejectsRawTextInReferenceIds() {
    User user =
        userRepository.save(User.create("unsafe-trace@example.com", "encoded", "unsafe"));
    AgentRunResult valid = missingFxRun(UUID.randomUUID().toString());
    List<AgentTraceStep> unsafeSteps =
        List.of(
            valid.steps().get(0),
            new AgentTraceStep(
                "model-1",
                "root",
                AgentTraceStepType.MODEL,
                "interpretQuestion",
                AgentTraceStepStatus.SUCCESS,
                5,
                null,
                List.of("사용자의 실제 질문 원문")));
    AgentRunResult unsafe =
        new AgentRunResult(
            valid.traceId(),
            valid.promptVersion(),
            valid.model(),
            valid.status(),
            valid.conclusion(),
            valid.finalAnswer(),
            unsafeSteps,
            valid.evidenceFacts(),
            valid.safetyViolations(),
            valid.latencyMs(),
            valid.tokenUsage());

    assertThatThrownBy(() -> harness.evaluateAndRecord(user.getId(), "missing-fx", unsafe))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
  }

  private AgentRunResult missingFxRun(String traceId) {
    return new AgentRunResult(
        traceId,
        "financial-agent-v1",
        "not-connected-fixture",
        AgentRunStatus.COMPLETED,
        EvidenceConclusion.UNAVAILABLE,
        "현재 환율을 확인할 근거가 없어 원화 평가금액은 확인 불가입니다.",
        List.of(
            new AgentTraceStep(
                "root",
                null,
                AgentTraceStepType.AGENT,
                "financialEvidenceAgent",
                AgentTraceStepStatus.SUCCESS,
                42,
                null,
                List.of()),
            new AgentTraceStep(
                "model-1",
                "root",
                AgentTraceStepType.MODEL,
                "interpretQuestion",
                AgentTraceStepStatus.SUCCESS,
                10,
                null,
                List.of()),
            new AgentTraceStep(
                "tool-1",
                "model-1",
                AgentTraceStepType.TOOL,
                "getAssetEvidence",
                AgentTraceStepStatus.SUCCESS,
                12,
                null,
                List.of("asset:11", "evidence-trace:abc")),
            new AgentTraceStep(
                "guardrail-1",
                "tool-1",
                AgentTraceStepType.GUARDRAIL,
                "financialSafetyPolicy",
                AgentTraceStepStatus.SUCCESS,
                2,
                null,
                List.of()),
            new AgentTraceStep(
                "model-2",
                "guardrail-1",
                AgentTraceStepType.MODEL,
                "composeGroundedAnswer",
                AgentTraceStepStatus.SUCCESS,
                18,
                null,
                List.of())),
        Set.of("FX_MISSING", "exchangeRate=null", "valuationKrw=null"),
        Set.of(),
        42,
        new AgentTokenUsage(120L, 38L));
  }
}
