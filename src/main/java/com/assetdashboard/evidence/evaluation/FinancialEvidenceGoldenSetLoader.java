package com.assetdashboard.evidence.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 애플리케이션과 테스트가 동일한 JSONL 골든셋을 읽게 한다. */
@Component
@RequiredArgsConstructor
public class FinancialEvidenceGoldenSetLoader {

  public static final String GOLDEN_SET =
      "/evaluation/financial-evidence-golden-set.jsonl";

  private final ObjectMapper objectMapper;

  public List<FinancialEvidenceGoldenCase> load() {
    InputStream stream = getClass().getResourceAsStream(GOLDEN_SET);
    if (stream == null) {
      throw new IllegalStateException("골든셋 resource를 찾을 수 없습니다: " + GOLDEN_SET);
    }
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader.lines().filter(line -> !line.isBlank()).map(this::readCase).toList();
    } catch (IOException e) {
      throw new IllegalStateException("골든셋을 읽을 수 없습니다.", e);
    }
  }

  public FinancialEvidenceGoldenCase requireCase(String caseId) {
    return load().stream()
        .filter(goldenCase -> goldenCase.id().equals(caseId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 골든셋 caseId입니다: " + caseId));
  }

  private FinancialEvidenceGoldenCase readCase(String line) {
    try {
      return objectMapper.readValue(line, FinancialEvidenceGoldenCase.class);
    } catch (IOException e) {
      throw new IllegalStateException("골든셋 JSONL을 읽을 수 없습니다: " + line, e);
    }
  }
}
