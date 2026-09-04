package com.assetdashboard.evidence.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse.AssetCalculation;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse.ExchangeRateEvidence;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse.PriceEvidence;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse.TransactionEvidence;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.evidence.calculation.EvidenceValueStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NvidiaNimFinancialAgentModelClientTest {

  @Test
  void performsForcedToolCallThenComposesAnswerWithoutLettingModelCreateFacts() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://nim.test/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    FinancialAgentProperties properties =
        new FinancialAgentProperties(
            true,
            "https://nim.test/v1",
            "test-key",
            "nvidia/nemotron-3.5-lightning-30b-a3b",
            1000,
            1000,
            true,
            0,
            0);
    NvidiaNimFinancialAgentModelClient client =
        new NvidiaNimFinancialAgentModelClient(
            builder.build(), new ObjectMapper().findAndRegisterModules(), properties);

    server
        .expect(requestTo("https://nim.test/v1/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-key"))
        .andExpect(jsonPath("$.tools[0].function.name").value("getAssetEvidence"))
        .andExpect(jsonPath("$.tool_choice.function.name").value("getAssetEvidence"))
        .andExpect(jsonPath("$.parallel_tool_calls").value(false))
        .andExpect(jsonPath("$.chat_template_kwargs.enable_thinking").value(false))
        .andExpect(jsonPath("$.messages[0].tool_calls").doesNotExist())
        .andExpect(jsonPath("$.messages[0].tool_call_id").doesNotExist())
        .andRespond(
            withSuccess(
                """
                {
                  "model":"nvidia/nemotron-3.5-lightning-30b-a3b",
                  "choices":[{
                    "message":{"content":null,"tool_calls":[{
                      "id":"call-42",
                      "type":"function",
                      "function":{"name":"getAssetEvidence","arguments":"{\\"assetId\\":42}"}
                    }]},
                    "finish_reason":"tool_calls"
                  }],
                  "usage":{"prompt_tokens":120,"completion_tokens":12,"total_tokens":132}
                }
                """,
                MediaType.APPLICATION_JSON));

    server
        .expect(requestTo("https://nim.test/v1/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.messages[2].tool_calls[0].id").value("call-42"))
        .andExpect(jsonPath("$.messages[3].role").value("tool"))
        .andExpect(jsonPath("$.messages[3].tool_call_id").value("call-42"))
        .andExpect(jsonPath("$.tool_choice").value("none"))
        .andRespond(
            withSuccess(
                """
                {
                  "model":"nvidia/nemotron-3.5-lightning-30b-a3b",
                  "choices":[{
                    "message":{"content":"현재 환율 근거가 없어 원화 평가금액은 확인 불가입니다."},
                    "finish_reason":"stop"
                  }],
                  "usage":{"prompt_tokens":350,"completion_tokens":25,"total_tokens":375}
                }
                """,
                MediaType.APPLICATION_JSON));

    AgentToolCallResponse toolCall =
        client.requestTool(
            "42번 자산의 환율을 확인해줘.", 42L, "getAssetEvidence");

    assertThat(toolCall.toolName()).isEqualTo("getAssetEvidence");
    assertThat(toolCall.assetId()).isEqualTo(42L);
    assertThat(toolCall.tokenUsage().inputTokens()).isEqualTo(120L);
    assertThat(toolCall.tokenUsage().outputTokens()).isEqualTo(12L);

    AgentModelResponse answer =
        client.composeGroundedAnswer(
            "42번 자산의 환율을 확인해줘.",
            toolCall,
            missingFxEvidence(),
            EvidenceConclusion.UNAVAILABLE);

    assertThat(answer.finalAnswer()).contains("확인 불가");
    assertThat(answer.tokenUsage().inputTokens()).isEqualTo(350L);
    assertThat(answer.tokenUsage().outputTokens()).isEqualTo(25L);
    server.verify();
  }

  @Test
  void forcesPriceTrendToolForDirectionQuestion() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://nim.test/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    FinancialAgentProperties properties =
        new FinancialAgentProperties(
            true,
            "https://nim.test/v1",
            "test-key",
            "nvidia/nemotron-3.5-lightning-30b-a3b",
            1000,
            1000,
            true,
            0,
            0);
    NvidiaNimFinancialAgentModelClient client =
        new NvidiaNimFinancialAgentModelClient(
            builder.build(), new ObjectMapper().findAndRegisterModules(), properties);

    server
        .expect(requestTo("https://nim.test/v1/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.tools[0].function.name").value("getPriceTrendEvidence"))
        .andExpect(jsonPath("$.tool_choice.function.name").value("getPriceTrendEvidence"))
        .andRespond(
            withSuccess(
                """
                {
                  "model":"nvidia/nemotron-3.5-lightning-30b-a3b",
                  "choices":[{
                    "message":{"content":null,"tool_calls":[{
                      "id":"call-trend-42",
                      "type":"function",
                      "function":{"name":"getPriceTrendEvidence","arguments":"{\\"assetId\\":42}"}
                    }]},
                    "finish_reason":"tool_calls"
                  }],
                  "usage":{"prompt_tokens":100,"completion_tokens":10,"total_tokens":110}
                }
                """,
                MediaType.APPLICATION_JSON));

    AgentToolCallResponse toolCall =
        client.requestTool(
            "최근 가격 방향을 알려줘", 42L, "getPriceTrendEvidence");

    assertThat(toolCall.toolName()).isEqualTo("getPriceTrendEvidence");
    assertThat(toolCall.assetId()).isEqualTo(42L);
    server.verify();
  }

  private AssetEvidenceResponse missingFxEvidence() {
    LocalDateTime generatedAt = LocalDateTime.of(2026, 9, 2, 12, 0);
    return new AssetEvidenceResponse(
        "evidence-trace-42",
        generatedAt,
        EvidenceConclusion.UNAVAILABLE,
        new AssetCalculation(
            42L,
            AssetType.CRYPTO,
            "BTC",
            "BTC",
            "비트코인",
            "USDT",
            BigDecimal.ONE,
            new BigDecimal("84000000"),
            new BigDecimal("60000"),
            null,
            null,
            null,
            BigDecimal.ZERO,
            "quantity * currentPrice * exchangeRate",
            "valuationKrw - (quantity * averagePriceKrw)"),
        new PriceEvidence(
            new BigDecimal("65000"), AssetSource.API, generatedAt, EvidenceValueStatus.FRESH),
        new ExchangeRateEvidence(null, null, EvidenceValueStatus.MISSING),
        List.of(new AssetEvidenceResponse.EvidenceWarning("FX_MISSING", "현재 원화 환율을 확인할 수 없습니다.")),
        new TransactionEvidence(0, 0, false, List.of()));
  }
}
