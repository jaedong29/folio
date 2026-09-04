package com.assetdashboard.evidence.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetdashboard.evidence.calculation.AssetEvidenceResponse;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.evaluation.FinancialEvidenceEvaluationHarness;
import com.assetdashboard.evidence.evaluation.FinancialEvidenceGoldenCase;
import com.assetdashboard.evidence.evaluation.FinancialEvidenceGoldenSetLoader;
import com.assetdashboard.evidence.news.NewsAnswerGuardrail;
import com.assetdashboard.evidence.news.NewsEvidenceItem;
import com.assetdashboard.evidence.news.NewsEvidenceResponse;
import com.assetdashboard.evidence.news.NewsEvidenceToolAdapter;
import com.assetdashboard.evidence.news.NewsEvidenceToolResult;
import com.assetdashboard.evidence.tool.AssetEvidenceToolAdapter;
import com.assetdashboard.evidence.tool.AssetEvidenceToolResult;
import com.assetdashboard.evidence.trend.PriceTrendEvidenceToolAdapter;
import com.assetdashboard.evidence.trend.PriceTrendEvidenceToolResult;
import com.assetdashboard.evidence.trend.PriceTrendEvidenceResponse;
import com.assetdashboard.evidence.trend.PriceTrendAnswerGuardrail;
import com.assetdashboard.evidence.trend.PriceTrendDirection;
import com.assetdashboard.evidence.trace.AgentRunResult;
import com.assetdashboard.evidence.trace.AgentTokenUsage;
import com.assetdashboard.evidence.trace.AgentTraceService;
import com.assetdashboard.evidence.trace.AgentTraceResponse;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.news.NewsCategory;
import com.assetdashboard.news.NewsSourceType;
import com.assetdashboard.news.NewsTrust;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FinancialEvidenceAgentServiceTest {

  private final FinancialAgentModelClient modelClient = mock(FinancialAgentModelClient.class);
  private final FinancialQuestionIntentClassifier intentClassifier =
      new FinancialQuestionIntentClassifier();
  private final AssetEvidenceToolAdapter toolAdapter = mock(AssetEvidenceToolAdapter.class);
  private final PriceTrendEvidenceToolAdapter trendToolAdapter =
      mock(PriceTrendEvidenceToolAdapter.class);
  private final NewsEvidenceToolAdapter newsToolAdapter = mock(NewsEvidenceToolAdapter.class);
  private final AgentTraceService traceService = mock(AgentTraceService.class);
  private final FinancialEvidenceGoldenSetLoader goldenSetLoader =
      mock(FinancialEvidenceGoldenSetLoader.class);
  private final FinancialEvidenceEvaluationHarness evaluationHarness =
      mock(FinancialEvidenceEvaluationHarness.class);
  private final LlmUsageBudgetService budgetService = mock(LlmUsageBudgetService.class);
  private final FinancialEvidenceAgentService service =
      new FinancialEvidenceAgentService(
          modelClient,
          new FinancialAgentProperties(
              true,
              "https://nim.test/v1",
              "test-key",
              "nim-model",
              1000,
              1000,
              true,
              0,
              0),
          intentClassifier,
          toolAdapter,
          trendToolAdapter,
          new PriceTrendAnswerGuardrail(),
          newsToolAdapter,
          new NewsAnswerGuardrail(),
          new GroundedAgentRunAssembler(new EvidenceConclusionPolicy()),
          traceService,
          goldenSetLoader,
          evaluationHarness,
          budgetService);

  @Test
  void usesApplicationGroundingAndRecordsTotals() {
    AssetEvidenceResponse payload = mock(AssetEvidenceResponse.class);
    GroundedToolResult grounding =
        new GroundedToolResult(
            AssetEvidenceToolAdapter.TOOL_NAME,
            EvidenceConclusion.UNAVAILABLE,
            Set.of("FX_MISSING", "exchangeRate=null", "valuationKrw=null"),
            List.of("asset:42", "evidence-trace:abc"));
    AgentToolCallResponse toolCall =
        new AgentToolCallResponse(
            "call-42",
            AssetEvidenceToolAdapter.TOOL_NAME,
            42L,
            "nim-model",
            10,
            new AgentTokenUsage(120L, 12L));
    when(modelClient.requestTool("환율 확인", 42L, AssetEvidenceToolAdapter.TOOL_NAME))
        .thenReturn(toolCall);
    when(toolAdapter.execute(7L, 42L)).thenReturn(new AssetEvidenceToolResult(payload, grounding));
    when(modelClient.composeGroundedAnswer(
            "환율 확인", toolCall, payload, EvidenceConclusion.UNAVAILABLE))
        .thenReturn(
            new AgentModelResponse(
                "환율 근거가 없어 확인 불가입니다.",
                "nim-model",
                20,
                new AgentTokenUsage(300L, 25L)));

    FinancialAgentResponse response = service.ask(7L, 42L, "환율 확인");

    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(response.inputTokens()).isEqualTo(420L);
    assertThat(response.outputTokens()).isEqualTo(37L);
    assertThat(response.evidenceReferenceIds()).containsExactly("asset:42", "evidence-trace:abc");

    ArgumentCaptor<AgentRunResult> runCaptor = ArgumentCaptor.forClass(AgentRunResult.class);
    verify(traceService).record(eq(7L), eq(null), eq("환율 확인"), runCaptor.capture(), eq(null));
    assertThat(runCaptor.getValue().evidenceFacts())
        .containsExactlyInAnyOrder("FX_MISSING", "exchangeRate=null", "valuationKrw=null");
    assertThat(runCaptor.getValue().steps())
        .extracting(step -> step.name())
        .containsExactly(
            "financialEvidenceAgent",
            "selectTool",
            "getAssetEvidence",
            "structuredEvidenceOnly",
            "composeGroundedAnswer");
  }

  @Test
  void rejectsModelAssetIdThatDiffersFromAuthenticatedRequestPath() {
    when(modelClient.requestTool(any(), eq(42L), eq(AssetEvidenceToolAdapter.TOOL_NAME)))
        .thenReturn(
            new AgentToolCallResponse(
                "call-other",
                AssetEvidenceToolAdapter.TOOL_NAME,
                99L,
                "nim-model",
                10,
                AgentTokenUsage.unknown()));

    assertThatThrownBy(() -> service.ask(7L, 42L, "42번 자산 확인"))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            error ->
                assertThat(((BusinessException) error).getErrorCode())
                    .isEqualTo(ErrorCode.AI_PROVIDER_INVALID_RESPONSE));
  }

  @Test
  void evaluatesTheSameExecutionPathWithGoldenQuestion() {
    FinancialEvidenceGoldenCase goldenCase =
        new FinancialEvidenceGoldenCase(
            "missing-fx",
            "현재 환율을 확인할 수 없는 외화 자산이 있어?",
            List.of(AssetEvidenceToolAdapter.TOOL_NAME),
            EvidenceConclusion.UNAVAILABLE,
            List.of("FX_MISSING", "exchangeRate=null", "valuationKrw=null"),
            List.of("exchangeRate=1"),
            "missing-fx");
    AssetEvidenceResponse payload = mock(AssetEvidenceResponse.class);
    GroundedToolResult grounding =
        new GroundedToolResult(
            AssetEvidenceToolAdapter.TOOL_NAME,
            EvidenceConclusion.UNAVAILABLE,
            Set.of("FX_MISSING", "exchangeRate=null", "valuationKrw=null"),
            List.of("asset:42", "evidence-trace:abc"));
    AgentToolCallResponse toolCall =
        new AgentToolCallResponse(
            "call-42",
            AssetEvidenceToolAdapter.TOOL_NAME,
            42L,
            "nim-model",
            10,
            new AgentTokenUsage(120L, 12L));
    AgentTraceResponse recorded = mock(AgentTraceResponse.class);

    when(goldenSetLoader.requireCase("missing-fx")).thenReturn(goldenCase);
    when(modelClient.requestTool(
            goldenCase.question(), 42L, AssetEvidenceToolAdapter.TOOL_NAME))
        .thenReturn(toolCall);
    when(toolAdapter.execute(7L, 42L)).thenReturn(new AssetEvidenceToolResult(payload, grounding));
    when(modelClient.composeGroundedAnswer(
            goldenCase.question(), toolCall, payload, EvidenceConclusion.UNAVAILABLE))
        .thenReturn(
            new AgentModelResponse(
                "현재 환율 근거가 없어 확인 불가입니다.",
                "nim-model",
                20,
                new AgentTokenUsage(300L, 25L)));
    when(evaluationHarness.evaluateAndRecord(eq(7L), eq("missing-fx"), any()))
        .thenReturn(recorded);

    assertThat(service.evaluate(7L, 42L, "missing-fx")).isSameAs(recorded);

    ArgumentCaptor<AgentRunResult> runCaptor = ArgumentCaptor.forClass(AgentRunResult.class);
    verify(evaluationHarness)
        .evaluateAndRecord(eq(7L), eq("missing-fx"), runCaptor.capture());
    assertThat(runCaptor.getValue().conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(runCaptor.getValue().evidenceFacts())
        .contains("FX_MISSING", "exchangeRate=null", "valuationKrw=null");
  }

  @Test
  void routesDirectionQuestionToStructuredPriceTrendEvidence() {
    String question = "현재 symbol의 방향성에 대해 근거를 좀 줘";
    PriceTrendEvidenceResponse payload = mock(PriceTrendEvidenceResponse.class);
    when(payload.direction()).thenReturn(PriceTrendDirection.UP);
    GroundedToolResult grounding =
        new GroundedToolResult(
            PriceTrendEvidenceToolAdapter.TOOL_NAME,
            EvidenceConclusion.CONFIRMED,
            Set.of(
                "PRICE_TREND_AVAILABLE",
                "direction",
                "direction=UP",
                "returnRatePercent"),
            List.of("asset:42", "price-trend:abc"));
    AgentToolCallResponse toolCall =
        new AgentToolCallResponse(
            "call-trend",
            PriceTrendEvidenceToolAdapter.TOOL_NAME,
            42L,
            "nim-model",
            10,
            new AgentTokenUsage(100L, 10L));
    when(modelClient.requestTool(
            question, 42L, PriceTrendEvidenceToolAdapter.TOOL_NAME))
        .thenReturn(toolCall);
    when(trendToolAdapter.execute(7L, 42L))
        .thenReturn(new PriceTrendEvidenceToolResult(payload, grounding));
    when(modelClient.composeGroundedAnswer(
            question, toolCall, payload, EvidenceConclusion.CONFIRMED))
        .thenReturn(
            new AgentModelResponse(
                "최근 가격 변화율 근거상 상승 방향입니다.",
                "nim-model",
                20,
                new AgentTokenUsage(200L, 20L)));

    FinancialAgentResponse response = service.ask(7L, 42L, question);

    assertThat(response.intent()).isEqualTo(FinancialQuestionIntent.PRICE_TREND);
    assertThat(response.toolsUsed())
        .containsExactly(PriceTrendEvidenceToolAdapter.TOOL_NAME);
    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.CONFIRMED);
    assertThat(response.evidenceReferenceIds())
        .containsExactly("asset:42", "price-trend:abc");
    verify(toolAdapter, never()).execute(any(), any());
  }

  @Test
  void evaluatesRegisteredAssetDirectionWithPriceTrendGoldenCase() {
    FinancialEvidenceGoldenCase goldenCase =
        new FinancialEvidenceGoldenCase(
            "price-direction",
            "현재 symbol의 최근 7일 가격 방향성과 근거를 알려줘.",
            List.of(PriceTrendEvidenceToolAdapter.TOOL_NAME),
            EvidenceConclusion.CONFIRMED,
            List.of("PRICE_TREND_AVAILABLE", "direction", "returnRatePercent"),
            List.of("평가손익률"),
            "registered-symbol-with-price-history");
    PriceTrendEvidenceResponse payload = mock(PriceTrendEvidenceResponse.class);
    when(payload.direction()).thenReturn(PriceTrendDirection.UP);
    GroundedToolResult grounding =
        new GroundedToolResult(
            PriceTrendEvidenceToolAdapter.TOOL_NAME,
            EvidenceConclusion.CONFIRMED,
            Set.of("PRICE_TREND_AVAILABLE", "direction", "returnRatePercent"),
            List.of("asset:42", "price-trend:abc"));
    AgentToolCallResponse toolCall =
        new AgentToolCallResponse(
            "call-trend-eval",
            PriceTrendEvidenceToolAdapter.TOOL_NAME,
            42L,
            "nim-model",
            10,
            AgentTokenUsage.unknown());
    AgentTraceResponse recorded = mock(AgentTraceResponse.class);
    when(goldenSetLoader.requireCase("price-direction")).thenReturn(goldenCase);
    when(modelClient.requestTool(
            goldenCase.question(), 42L, PriceTrendEvidenceToolAdapter.TOOL_NAME))
        .thenReturn(toolCall);
    when(trendToolAdapter.execute(7L, 42L))
        .thenReturn(new PriceTrendEvidenceToolResult(payload, grounding));
    when(modelClient.composeGroundedAnswer(
            goldenCase.question(), toolCall, payload, EvidenceConclusion.CONFIRMED))
        .thenReturn(
            new AgentModelResponse(
                "최근 가격 이력 기준 상승 방향입니다.",
                "nim-model",
                20,
                AgentTokenUsage.unknown()));
    when(evaluationHarness.evaluateAndRecord(eq(7L), eq("price-direction"), any()))
        .thenReturn(recorded);

    assertThat(service.evaluate(7L, 42L, "price-direction")).isSameAs(recorded);
    verify(evaluationHarness)
        .evaluateAndRecord(eq(7L), eq("price-direction"), any(AgentRunResult.class));
  }

  @Test
  void doesNotAskModelToInventDirectionWhenPriceHistoryIsUnavailable() {
    String question = "최근 가격 추세를 알려줘";
    PriceTrendEvidenceResponse payload = mock(PriceTrendEvidenceResponse.class);
    when(payload.direction()).thenReturn(PriceTrendDirection.UNAVAILABLE);
    GroundedToolResult grounding =
        new GroundedToolResult(
            PriceTrendEvidenceToolAdapter.TOOL_NAME,
            EvidenceConclusion.UNAVAILABLE,
            Set.of(
                "PRICE_HISTORY_MISSING",
                "direction=UNAVAILABLE",
                "returnRatePercent=null"),
            List.of("asset:42", "price-trend:missing"));
    AgentToolCallResponse toolCall =
        new AgentToolCallResponse(
            "call-trend-missing",
            PriceTrendEvidenceToolAdapter.TOOL_NAME,
            42L,
            "nim-model",
            10,
            new AgentTokenUsage(100L, 10L));
    when(modelClient.requestTool(
            question, 42L, PriceTrendEvidenceToolAdapter.TOOL_NAME))
        .thenReturn(toolCall);
    when(trendToolAdapter.execute(7L, 42L))
        .thenReturn(new PriceTrendEvidenceToolResult(payload, grounding));

    FinancialAgentResponse response = service.ask(7L, 42L, question);

    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(response.answer()).contains("가격 이력이 부족", "평가손익률");
    verify(modelClient, never()).composeGroundedAnswer(any(), any(), any(), any());

    ArgumentCaptor<AgentRunResult> runCaptor = ArgumentCaptor.forClass(AgentRunResult.class);
    verify(traceService).record(eq(7L), eq(null), eq(question), runCaptor.capture(), eq(null));
    assertThat(runCaptor.getValue().steps())
        .extracting(step -> step.name())
        .contains("deterministicUnavailableAnswer");
  }

  @Test
  void routesNewsQuestionToSharedReadOnlyNewsTool() {
    String question = "ZEC 최신 공식 개발 소식을 알려줘";
    NewsEvidenceItem item =
        new NewsEvidenceItem(
            9L,
            NewsCategory.CRYPTO,
            NewsSourceType.OFFICIAL_RELEASE,
            NewsTrust.VERIFIED_OFFICIAL,
            "Zebra 3.0.0",
            "Zcash Foundation",
            "https://example.com/release",
            Instant.parse("2026-09-02T00:00:00Z"),
            "Release notes",
            Set.of("RELEASE"),
            true);
    NewsEvidenceResponse payload =
        new NewsEvidenceResponse(
            "news-trace",
            42L,
            "ZEC",
            1,
            EvidenceConclusion.PARTIAL,
            Instant.parse("2026-09-03T00:00:00Z"),
            List.of(item),
            List.of("인과관계 확인 불가"));
    GroundedToolResult grounding =
        new GroundedToolResult(
            NewsEvidenceToolAdapter.TOOL_NAME,
            EvidenceConclusion.PARTIAL,
            Set.of("NEWS_AVAILABLE", "VERIFIED_OFFICIAL", "untrustedContent=true"),
            List.of("asset:42", "news:9"));
    AgentToolCallResponse toolCall =
        new AgentToolCallResponse(
            "call-news",
            NewsEvidenceToolAdapter.TOOL_NAME,
            42L,
            "nim-model",
            10,
            new AgentTokenUsage(100L, 10L));
    when(modelClient.requestTool(question, 42L, NewsEvidenceToolAdapter.TOOL_NAME))
        .thenReturn(toolCall);
    when(newsToolAdapter.execute(7L, 42L))
        .thenReturn(new NewsEvidenceToolResult(payload, grounding));
    when(modelClient.composeGroundedAnswer(
            question, toolCall, payload, EvidenceConclusion.PARTIAL))
        .thenReturn(
            new AgentModelResponse(
                "Zcash Foundation의 최신 공식 릴리스입니다.",
                "nim-model",
                20,
                new AgentTokenUsage(200L, 20L)));

    FinancialAgentResponse response = service.ask(7L, 42L, question);

    assertThat(response.intent()).isEqualTo(FinancialQuestionIntent.SYMBOL_NEWS);
    assertThat(response.toolsUsed()).containsExactly(NewsEvidenceToolAdapter.TOOL_NAME);
    assertThat(response.evidenceReferenceIds()).containsExactly("asset:42", "news:9");
    verify(toolAdapter, never()).execute(any(), any());
    verify(trendToolAdapter, never()).execute(any(), any());
  }

  @Test
  void productionModeRoutesDeterministicallyAndSkipsModelWhenNewsIsUnavailable() {
    FinancialEvidenceAgentService fastService =
        new FinancialEvidenceAgentService(
            modelClient,
            new FinancialAgentProperties(
                true,
                "https://nim.test/v1",
                "test-key",
                "nim-model",
                1000,
                1000,
                false,
                0,
                0),
            intentClassifier,
            toolAdapter,
            trendToolAdapter,
            new PriceTrendAnswerGuardrail(),
            newsToolAdapter,
            new NewsAnswerGuardrail(),
            new GroundedAgentRunAssembler(new EvidenceConclusionPolicy()),
            traceService,
            goldenSetLoader,
            evaluationHarness,
            budgetService);
    String question = "이 자산의 최신 뉴스를 알려줘";
    NewsEvidenceResponse payload =
        new NewsEvidenceResponse(
            "news-empty",
            42L,
            "ZEC",
            0,
            EvidenceConclusion.UNAVAILABLE,
            Instant.parse("2026-09-03T00:00:00Z"),
            List.of(),
            List.of("자료 없음"));
    GroundedToolResult grounding =
        new GroundedToolResult(
            NewsEvidenceToolAdapter.TOOL_NAME,
            EvidenceConclusion.UNAVAILABLE,
            Set.of("NEWS_UNAVAILABLE", "newsCount=0"),
            List.of("asset:42"));
    when(newsToolAdapter.execute(7L, 42L))
        .thenReturn(new NewsEvidenceToolResult(payload, grounding));

    FinancialAgentResponse response = fastService.ask(7L, 42L, question);

    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(response.answer()).contains("확인할 수 없습니다");
    verify(modelClient, never()).requestTool(any(), any(), any());
    verify(modelClient, never()).composeGroundedAnswer(any(), any(), any(), any());
    ArgumentCaptor<AgentRunResult> runCaptor = ArgumentCaptor.forClass(AgentRunResult.class);
    verify(traceService).record(eq(7L), eq(null), eq(question), runCaptor.capture(), eq(null));
    assertThat(runCaptor.getValue().steps())
        .extracting(step -> step.name())
        .contains("deterministicIntentRoute", "deterministicUnavailableAnswer");
  }

  @Test
  void blocksAskWithoutCallingModelWhenDailyBudgetIsExceeded() {
    doThrow(new BusinessException(ErrorCode.AI_BUDGET_EXCEEDED))
        .when(budgetService)
        .ensureWithinBudget();

    assertThatThrownBy(() -> service.ask(7L, 42L, "AAPL 평가금액 알려줘"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.AI_BUDGET_EXCEEDED);

    verify(modelClient, never()).requestTool(any(), any(), any());
    verify(modelClient, never()).composeGroundedAnswer(any(), any(), any(), any());
  }
}
