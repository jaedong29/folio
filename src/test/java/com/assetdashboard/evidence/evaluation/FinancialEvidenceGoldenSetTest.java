package com.assetdashboard.evidence.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** LLM을 연결하기 전에 질문별 기대 Tool·판정·금지 주장을 고정한 골든셋 계약 검사. */
class FinancialEvidenceGoldenSetTest {

  private static final String GOLDEN_SET =
      "/evaluation/financial-evidence-golden-set.jsonl";
  private static final Set<String> ALLOWED_TOOLS =
      Set.of(
          "getAssetEvidence",
          "getPriceTrendEvidence",
          "searchSymbolNews",
          "searchSymbolEvidence",
          "getEvidenceDocument");

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void goldenSetHasUniqueScorableCasesBeforeLlmIntegration() throws Exception {
    List<GoldenCase> cases = loadCases();

    assertThat(cases).hasSizeBetween(10, 20);
    assertThat(cases).allSatisfy(this::assertScorable);
    assertThat(cases.stream().map(GoldenCase::id)).doesNotHaveDuplicates();
    assertThat(cases.stream().map(GoldenCase::expectedConclusion).collect(java.util.stream.Collectors.toSet()))
        .containsExactlyInAnyOrder(
            EvidenceConclusion.CONFIRMED,
            EvidenceConclusion.PARTIAL,
            EvidenceConclusion.UNAVAILABLE);
  }

  private List<GoldenCase> loadCases() throws Exception {
    InputStream stream = getClass().getResourceAsStream(GOLDEN_SET);
    assertThat(stream).as("골든셋 resource").isNotNull();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader.lines()
          .filter(line -> !line.isBlank())
          .map(this::readCase)
          .toList();
    }
  }

  private GoldenCase readCase(String line) {
    try {
      return objectMapper.readValue(line, GoldenCase.class);
    } catch (Exception e) {
      throw new AssertionError("골든셋 JSONL을 읽을 수 없습니다: " + line, e);
    }
  }

  private void assertScorable(GoldenCase goldenCase) {
    assertThat(goldenCase.id()).isNotBlank();
    assertThat(goldenCase.question()).isNotBlank();
    assertThat(goldenCase.expectedTools()).isNotEmpty().allMatch(ALLOWED_TOOLS::contains);
    assertThat(goldenCase.expectedConclusion()).isNotNull();
    assertThat(goldenCase.requiredEvidence()).isNotEmpty();
    assertThat(goldenCase.forbiddenClaims()).isNotEmpty();
    assertThat(goldenCase.fixture()).isNotBlank();
    assertThat(new HashSet<>(goldenCase.expectedTools()))
        .hasSameSizeAs(goldenCase.expectedTools());
  }

  private record GoldenCase(
      String id,
      String question,
      List<String> expectedTools,
      EvidenceConclusion expectedConclusion,
      List<String> requiredEvidence,
      List<String> forbiddenClaims,
      String fixture) {}
}
