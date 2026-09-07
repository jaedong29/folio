package com.assetdashboard.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.assetdashboard.evidence.agent.FinancialAgentProperties;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.global.resilience.ExternalCallResilienceTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NvidiaNimNewsSummaryModelClientTest {

  @Test
  void limitsOverlongModelTextAtTheLastCompleteSentence() {
    String firstSummarySentence = "가".repeat(220) + ".";
    String secondSummarySentence = "나".repeat(180) + ".";
    String firstSignificanceSentence = "다".repeat(100) + ".";
    String secondSignificanceSentence = "라".repeat(100) + ".";

    assertThat(
            NvidiaNimNewsSummaryModelClient.limitAtSentenceBoundary(
                firstSummarySentence + " " + secondSummarySentence, 350))
        .isEqualTo(firstSummarySentence);
    assertThat(
            NvidiaNimNewsSummaryModelClient.limitAtSentenceBoundary(
                firstSignificanceSentence + " " + secondSignificanceSentence, 180))
        .isEqualTo(firstSignificanceSentence);
  }

  @Test
  void usesEllipsisWhenThereIsNoUsefulSentenceBoundary() {
    String result =
        NvidiaNimNewsSummaryModelClient.limitAtSentenceBoundary("가".repeat(400), 350);

    assertThat(result).hasSizeLessThanOrEqualTo(350).endsWith("…");
  }

  @Test
  void doesNotTreatVersionDecimalPointAsSentenceBoundary() {
    String overlong = "가".repeat(340) + " 6.3 버전의 변경 내용";

    String result = NvidiaNimNewsSummaryModelClient.limitAtSentenceBoundary(overlong, 350);

    assertThat(result).hasSizeLessThanOrEqualTo(350).endsWith("…");
  }

  @Test
  void requestsOneNonStreamingJsonSummaryWithoutTools() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://nim.test/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    FinancialAgentProperties ai =
        new FinancialAgentProperties(
            true, "https://nim.test/v1", "test-key", "nemotron", 1000, 1000, false, 0, 0);
    NewsProperties news =
        new NewsProperties(true, 1000, 1000, 360, 10, 1000, true, 1200, 6000);
    NvidiaNimNewsSummaryModelClient client =
        new NvidiaNimNewsSummaryModelClient(
            builder.build(),
            new ObjectMapper().findAndRegisterModules(),
            ai,
            news,
            ExternalCallResilienceTestSupport.create());

    server
        .expect(requestTo("https://nim.test/v1/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-key"))
        .andExpect(jsonPath("$.stream").value(false))
        .andExpect(jsonPath("$.chat_template_kwargs.enable_thinking").value(false))
        .andExpect(jsonPath("$.tools").doesNotExist())
        .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("Zebra 6.3.0")))
        .andRespond(
            withSuccess(
                """
                {
                  "model":"nemotron",
                  "choices":[{"message":{"content":"{\\"summaryKo\\":\\"DNS 시더가 추가됐습니다.\\",\\"significanceKo\\":\\"노드 연결 경로를 보강하는 변경입니다.\\"}"}}],
                  "usage":{"prompt_tokens":120,"completion_tokens":30}
                }
                """,
                MediaType.APPLICATION_JSON));

    NewsSummaryDraft result =
        client.summarize(
            new ClaimedNewsSummary(
                1L,
                "hash",
                "Zebra 6.3.0",
                "Zcash Foundation",
                "DNS seeders were added."));

    assertThat(result.summaryKo()).isEqualTo("DNS 시더가 추가됐습니다.");
    assertThat(result.significanceKo()).isEqualTo("노드 연결 경로를 보강하는 변경입니다.");
    assertThat(result.inputTokens()).isEqualTo(120);
    assertThat(result.outputTokens()).isEqualTo(30);
    server.verify();
  }

  @Test
  void doesNotRetryPotentiallyChargeableNimPost() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://nim.test/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    FinancialAgentProperties ai =
        new FinancialAgentProperties(
            true, "https://nim.test/v1", "test-key", "nemotron", 1000, 1000, false, 0, 0);
    NewsProperties news =
        new NewsProperties(true, 1000, 1000, 360, 10, 1000, true, 1200, 6000);
    NvidiaNimNewsSummaryModelClient client =
        new NvidiaNimNewsSummaryModelClient(
            builder.build(),
            new ObjectMapper().findAndRegisterModules(),
            ai,
            news,
            ExternalCallResilienceTestSupport.create());
    server.expect(requestTo("https://nim.test/v1/chat/completions")).andRespond(withServerError());

    assertThatThrownBy(
            () ->
                client.summarize(
                    new ClaimedNewsSummary(1L, "hash", "title", "publisher", "content")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE));
    server.verify();
  }
}
