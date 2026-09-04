package com.assetdashboard.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 실제 NIM 재검증에서 발견된 깨진 토큰(_sync 성능)과 문장수 위반을 포함해
 * NewsSummaryGuardrail을 회귀 검증하는 골든셋.
 */
class NewsSummaryQualityGoldenSetTest {

  private static final String GOLDEN_SET = "/evaluation/news-summary-quality-golden-set.jsonl";
  private static final List<String> REQUIRED_CATEGORIES =
      List.of(
          "NORMAL",
          "MALFORMED_TOKEN",
          "SENTENCE_COUNT",
          "UNSUPPORTED_NUMBER",
          "PRICE_CAUSATION",
          "PROMPT_INJECTION");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final NewsSummaryGuardrail guardrail = new NewsSummaryGuardrail();

  @Test
  void goldenSetCoversEveryQualityCategoryExactlyOnce() throws Exception {
    List<QualityCase> cases = loadCases();

    assertThat(cases.stream().map(QualityCase::id)).doesNotHaveDuplicates();
    assertThat(cases.stream().map(QualityCase::category))
        .containsExactlyInAnyOrderElementsOf(REQUIRED_CATEGORIES);
  }

  @Test
  void guardrailMatchesExpectedVerdictForEveryCase() throws Exception {
    for (QualityCase qualityCase : loadCases()) {
      ClaimedNewsSummary source =
          new ClaimedNewsSummary(
              1L, "hash", qualityCase.title(), "Zcash Foundation", qualityCase.content());
      NewsSummaryDraft draft =
          new NewsSummaryDraft(
              qualityCase.summaryKo(), qualityCase.significanceKo(), "model", 10, 20, 5);

      Optional<String> violation = guardrail.validate(source, draft);

      if (qualityCase.expectedBlocked()) {
        assertThat(violation)
            .as("case=%s (%s)", qualityCase.id(), qualityCase.note())
            .contains(qualityCase.expectedFailureCode());
      } else {
        assertThat(violation).as("case=%s (%s)", qualityCase.id(), qualityCase.note()).isEmpty();
      }
    }
  }

  private List<QualityCase> loadCases() throws Exception {
    InputStream stream = getClass().getResourceAsStream(GOLDEN_SET);
    assertThat(stream).as("골든셋 resource").isNotNull();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader.lines().filter(line -> !line.isBlank()).map(this::readCase).toList();
    }
  }

  private QualityCase readCase(String line) {
    try {
      return objectMapper.readValue(line, QualityCase.class);
    } catch (Exception e) {
      throw new AssertionError("골든셋 JSONL을 읽을 수 없습니다: " + line, e);
    }
  }

  private record QualityCase(
      String id,
      String category,
      String title,
      String content,
      String summaryKo,
      String significanceKo,
      boolean expectedBlocked,
      String expectedFailureCode,
      String note) {}
}
