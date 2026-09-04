package com.assetdashboard.evidence.agent;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.evaluation.FinancialEvidenceEvaluationHarness;
import com.assetdashboard.evidence.evaluation.FinancialEvidenceGoldenCase;
import com.assetdashboard.evidence.evaluation.FinancialEvidenceGoldenSetLoader;
import com.assetdashboard.evidence.news.NewsAnswerGuardrail;
import com.assetdashboard.evidence.news.NewsAnswerGuardrail.GuardrailDecision;
import com.assetdashboard.evidence.news.NewsEvidenceResponse;
import com.assetdashboard.evidence.news.NewsEvidenceToolAdapter;
import com.assetdashboard.evidence.news.NewsEvidenceToolResult;
import com.assetdashboard.evidence.tool.AssetEvidenceToolAdapter;
import com.assetdashboard.evidence.tool.AssetEvidenceToolResult;
import com.assetdashboard.evidence.trace.AgentRunResult;
import com.assetdashboard.evidence.trace.AgentRunStatus;
import com.assetdashboard.evidence.trace.AgentTokenUsage;
import com.assetdashboard.evidence.trace.AgentTraceService;
import com.assetdashboard.evidence.trace.AgentTraceResponse;
import com.assetdashboard.evidence.trace.AgentTraceStep;
import com.assetdashboard.evidence.trace.AgentTraceStepStatus;
import com.assetdashboard.evidence.trace.AgentTraceStepType;
import com.assetdashboard.evidence.trend.PriceTrendAnswerGuardrail;
import com.assetdashboard.evidence.trend.PriceTrendEvidenceResponse;
import com.assetdashboard.evidence.trend.PriceTrendEvidenceToolAdapter;
import com.assetdashboard.evidence.trend.PriceTrendEvidenceToolResult;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 모델의 Tool 요청을 기존 읽기 전용 Evidence 서비스에 연결하는 단일 자산 Agent. */
@Service
@RequiredArgsConstructor
public class FinancialEvidenceAgentService {

  static final String PROMPT_VERSION = "financial-agent-v3";

  private final FinancialAgentModelClient modelClient;
  private final FinancialAgentProperties properties;
  private final FinancialQuestionIntentClassifier intentClassifier;
  private final AssetEvidenceToolAdapter assetEvidenceToolAdapter;
  private final PriceTrendEvidenceToolAdapter priceTrendEvidenceToolAdapter;
  private final PriceTrendAnswerGuardrail priceTrendAnswerGuardrail;
  private final NewsEvidenceToolAdapter newsEvidenceToolAdapter;
  private final NewsAnswerGuardrail newsAnswerGuardrail;
  private final GroundedAgentRunAssembler assembler;
  private final AgentTraceService traceService;
  private final FinancialEvidenceGoldenSetLoader goldenSetLoader;
  private final FinancialEvidenceEvaluationHarness evaluationHarness;
  private final LlmUsageBudgetService budgetService;

  public FinancialAgentResponse ask(Long userId, Long assetId, String question) {
    Execution execution = execute(userId, assetId, question, properties.strictToolSelection());
    AgentRunResult result = execution.result();
    traceService.record(userId, null, question, result, null);

    return new FinancialAgentResponse(
        result.traceId(),
        result.conclusion(),
        result.finalAnswer(),
        result.model(),
        result.latencyMs(),
        result.tokenUsage().inputTokens(),
        result.tokenUsage().outputTokens(),
        execution.evidenceReferenceIds(),
        execution.intent(),
        List.of(execution.toolName()));
  }

  public AgentTraceResponse evaluate(Long userId, Long assetId, String caseId) {
    FinancialEvidenceGoldenCase goldenCase = goldenSetLoader.requireCase(caseId);
    if (goldenCase.expectedTools().size() != 1) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "현재 실행 가능한 평가는 단일 Tool 사례뿐입니다.");
    }
    FinancialQuestionIntent intent = intentClassifier.classify(goldenCase.question());
    if (!goldenCase.expectedTools().get(0).equals(intent.requiredToolName())) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "골든 케이스의 질문 의도와 기대 Tool이 일치하지 않습니다.");
    }
    Execution execution = execute(userId, assetId, goldenCase.question(), true);
    return evaluationHarness.evaluateAndRecord(userId, caseId, execution.result());
  }

  private Execution execute(
      Long userId, Long assetId, String question, boolean forceModelToolSelection) {
    FinancialQuestionIntent intent = intentClassifier.classify(question);
    String requiredToolName = intent.requiredToolName();
    AgentToolCallResponse toolCall =
        forceModelToolSelection
            ? requestToolWithBudget(question, assetId, requiredToolName)
            : deterministicToolCall(assetId, requiredToolName);
    validateToolCall(toolCall, assetId, requiredToolName);

    long toolStartedAt = System.nanoTime();
    ToolExecution toolExecution = executeTool(userId, assetId, requiredToolName);
    long toolLatencyMs = elapsedMs(toolStartedAt);

    AnswerExecution answerExecution = answer(question, toolCall, toolExecution, intent);
    AgentModelResponse answer = answerExecution.answer();
    AgentTokenUsage totalUsage = addUsage(toolCall.tokenUsage(), answer.tokenUsage());
    long totalRunLatency = toolCall.latencyMs() + toolLatencyMs + answer.latencyMs();
    AgentModelResponse combinedModelResponse =
        new AgentModelResponse(
            answer.finalAnswer(), answer.model(), totalRunLatency, totalUsage);

    String traceId = UUID.randomUUID().toString();
    AgentRunResult result =
        assembler.assemble(
            traceId,
            PROMPT_VERSION,
            AgentRunStatus.COMPLETED,
            combinedModelResponse,
            traceSteps(
                toolCall,
                toolExecution,
                toolLatencyMs,
                answer.latencyMs(),
                totalRunLatency,
                answerExecution,
                intent,
                forceModelToolSelection),
            List.of(toolExecution.grounding()),
            Set.of());
    return new Execution(
        result,
        toolExecution.grounding().referenceIds(),
        intent,
        toolExecution.grounding().toolName());
  }

  private AgentToolCallResponse requestToolWithBudget(
      String question, Long assetId, String requiredToolName) {
    budgetService.ensureWithinBudget();
    AgentToolCallResponse response = modelClient.requestTool(question, assetId, requiredToolName);
    recordModelUsage(response.tokenUsage());
    return response;
  }

  private AgentModelResponse composeGroundedAnswerWithBudget(
      String question,
      AgentToolCallResponse toolCall,
      Object evidence,
      EvidenceConclusion answerConclusion) {
    budgetService.ensureWithinBudget();
    AgentModelResponse response =
        modelClient.composeGroundedAnswer(question, toolCall, evidence, answerConclusion);
    recordModelUsage(response.tokenUsage());
    return response;
  }

  private void recordModelUsage(AgentTokenUsage usage) {
    budgetService.recordUsage(
        usage.inputTokens() == null ? 0 : usage.inputTokens(),
        usage.outputTokens() == null ? 0 : usage.outputTokens());
  }

  private ToolExecution executeTool(Long userId, Long assetId, String toolName) {
    if (AssetEvidenceToolAdapter.TOOL_NAME.equals(toolName)) {
      AssetEvidenceToolResult result = assetEvidenceToolAdapter.execute(userId, assetId);
      return new ToolExecution(result.payload(), result.grounding());
    }
    if (PriceTrendEvidenceToolAdapter.TOOL_NAME.equals(toolName)) {
      PriceTrendEvidenceToolResult result = priceTrendEvidenceToolAdapter.execute(userId, assetId);
      return new ToolExecution(result.payload(), result.grounding());
    }
    if (NewsEvidenceToolAdapter.TOOL_NAME.equals(toolName)) {
      NewsEvidenceToolResult result = newsEvidenceToolAdapter.execute(userId, assetId);
      return new ToolExecution(result.payload(), result.grounding());
    }
    throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 금융 Agent Tool입니다.");
  }

  private AnswerExecution answer(
      String question,
      AgentToolCallResponse toolCall,
      ToolExecution toolExecution,
      FinancialQuestionIntent intent) {
    if (isDeterministicUnavailable(intent, toolExecution.grounding())) {
      if (intent == FinancialQuestionIntent.SYMBOL_NEWS) {
        GuardrailDecision decision =
            newsAnswerGuardrail.apply(
                null, (NewsEvidenceResponse) toolExecution.payload());
        return new AnswerExecution(
            new AgentModelResponse(
                decision.answer(), toolCall.model(), 0, new AgentTokenUsage(0L, 0L)),
            true,
            false,
            decision.violationCode());
      }
      PriceTrendAnswerGuardrail.GuardrailDecision decision =
          priceTrendAnswerGuardrail.apply(
              null, (PriceTrendEvidenceResponse) toolExecution.payload());
      return new AnswerExecution(
          new AgentModelResponse(
              decision.answer(), toolCall.model(), 0, new AgentTokenUsage(0L, 0L)),
          true,
          false,
          decision.violationCode());
    }
    AgentModelResponse modelAnswer =
        composeGroundedAnswerWithBudget(
            question,
            toolCall,
            toolExecution.payload(),
            toolExecution.grounding().conclusion());
    if (intent != FinancialQuestionIntent.PRICE_TREND) {
      if (intent == FinancialQuestionIntent.SYMBOL_NEWS) {
        GuardrailDecision decision =
            newsAnswerGuardrail.apply(
                modelAnswer.finalAnswer(), (NewsEvidenceResponse) toolExecution.payload());
        AgentModelResponse guardedAnswer =
            decision.replaced()
                ? new AgentModelResponse(
                    decision.answer(),
                    modelAnswer.model(),
                    modelAnswer.latencyMs(),
                    modelAnswer.tokenUsage())
                : modelAnswer;
        return new AnswerExecution(
            guardedAnswer, false, decision.replaced(), decision.violationCode());
      }
      return new AnswerExecution(modelAnswer, false, false, null);
    }
    PriceTrendAnswerGuardrail.GuardrailDecision decision =
        priceTrendAnswerGuardrail.apply(
            modelAnswer.finalAnswer(), (PriceTrendEvidenceResponse) toolExecution.payload());
    AgentModelResponse guardedAnswer =
        decision.replaced()
            ? new AgentModelResponse(
                decision.answer(),
                modelAnswer.model(),
                modelAnswer.latencyMs(),
                modelAnswer.tokenUsage())
            : modelAnswer;
    return new AnswerExecution(
        guardedAnswer, false, decision.replaced(), decision.violationCode());
  }

  private boolean isDeterministicUnavailable(
      FinancialQuestionIntent intent, GroundedToolResult grounding) {
    return (intent == FinancialQuestionIntent.PRICE_TREND
            || intent == FinancialQuestionIntent.SYMBOL_NEWS)
        && grounding.conclusion() == EvidenceConclusion.UNAVAILABLE;
  }

  private void validateToolCall(
      AgentToolCallResponse toolCall, Long requestedAssetId, String requiredToolName) {
    if (!requiredToolName.equals(toolCall.toolName())) {
      throw new BusinessException(ErrorCode.AI_PROVIDER_INVALID_RESPONSE);
    }
    if (!requestedAssetId.equals(toolCall.assetId())) {
      throw new BusinessException(
          ErrorCode.AI_PROVIDER_INVALID_RESPONSE,
          "모델 Tool 인자가 요청 경로의 assetId와 일치하지 않습니다.");
    }
  }

  private AgentToolCallResponse deterministicToolCall(Long assetId, String requiredToolName) {
    return new AgentToolCallResponse(
        "route-" + UUID.randomUUID(),
        requiredToolName,
        assetId,
        properties.model(),
        0,
        new AgentTokenUsage(0L, 0L));
  }

  private List<AgentTraceStep> traceSteps(
      AgentToolCallResponse toolCall,
      ToolExecution toolExecution,
      long toolLatencyMs,
      long answerLatencyMs,
      long totalLatencyMs,
      AnswerExecution answerExecution,
      FinancialQuestionIntent intent,
      boolean modelSelectedTool) {
    List<AgentTraceStep> steps =
        new ArrayList<>(
            List.of(
                new AgentTraceStep(
                    "root",
                    null,
                    AgentTraceStepType.AGENT,
                    "financialEvidenceAgent",
                    AgentTraceStepStatus.SUCCESS,
                    totalLatencyMs,
                    null,
                    List.of()),
                new AgentTraceStep(
                    "model-plan",
                    "root",
                    modelSelectedTool ? AgentTraceStepType.MODEL : AgentTraceStepType.ROUTER,
                    modelSelectedTool ? "selectTool" : "deterministicIntentRoute",
                    AgentTraceStepStatus.SUCCESS,
                    toolCall.latencyMs(),
                    null,
                    List.of()),
                new AgentTraceStep(
                    "tool-evidence",
                    "model-plan",
                    AgentTraceStepType.TOOL,
                    toolCall.toolName(),
                    AgentTraceStepStatus.SUCCESS,
                    toolLatencyMs,
                    null,
                    toolExecution.grounding().referenceIds()),
                new AgentTraceStep(
                    "guardrail",
                    "tool-evidence",
                    AgentTraceStepType.GUARDRAIL,
                    "structuredEvidenceOnly",
                    AgentTraceStepStatus.SUCCESS,
                    0,
                    null,
                    List.of()),
                new AgentTraceStep(
                    "model-answer",
                    "guardrail",
                    answerExecution.skippedModel()
                        ? AgentTraceStepType.GUARDRAIL
                        : AgentTraceStepType.MODEL,
                    answerExecution.skippedModel()
                        ? "deterministicUnavailableAnswer"
                        : "composeGroundedAnswer",
                    AgentTraceStepStatus.SUCCESS,
                    answerLatencyMs,
                    null,
                    List.of())));
    if (answerExecution.modelAnswerReplaced()) {
      steps.add(
          new AgentTraceStep(
              "answer-guardrail",
              "model-answer",
              AgentTraceStepType.GUARDRAIL,
              intent == FinancialQuestionIntent.SYMBOL_NEWS
                  ? "newsCausalityValidation"
                  : "priceTrendClaimValidation",
              AgentTraceStepStatus.BLOCKED,
              0,
              answerExecution.guardrailCode(),
              List.of()));
    }
    return List.copyOf(steps);
  }

  private AgentTokenUsage addUsage(AgentTokenUsage first, AgentTokenUsage second) {
    return new AgentTokenUsage(
        addKnown(first.inputTokens(), second.inputTokens()),
        addKnown(first.outputTokens(), second.outputTokens()));
  }

  private Long addKnown(Long first, Long second) {
    return first == null || second == null ? null : first + second;
  }

  private long elapsedMs(long startedAt) {
    return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
  }

  private record ToolExecution(Object payload, GroundedToolResult grounding) {}

  private record AnswerExecution(
      AgentModelResponse answer,
      boolean skippedModel,
      boolean modelAnswerReplaced,
      String guardrailCode) {}

  private record Execution(
      AgentRunResult result,
      List<String> evidenceReferenceIds,
      FinancialQuestionIntent intent,
      String toolName) {}
}
