package com.assetdashboard.evidence.news;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** 뉴스 동시발생을 가격 원인으로 바꾸는 답변을 결정적으로 차단한다. */
@Component
public class NewsAnswerGuardrail {

  private static final List<String> CAUSALITY_TERMS =
      List.of(
          "뉴스 때문에 상승",
          "뉴스 때문에 하락",
          "뉴스로 인해 상승",
          "뉴스로 인해 하락",
          "직접적인 원인",
          "caused the price",
          "direct cause");

  public GuardrailDecision apply(String modelAnswer, NewsEvidenceResponse evidence) {
    if (evidence.items().isEmpty()) {
      return new GuardrailDecision(unavailableAnswer(evidence), true, "NEWS_EVIDENCE_UNAVAILABLE");
    }
    String normalized = modelAnswer == null ? "" : modelAnswer.toLowerCase(Locale.ROOT);
    boolean unsupportedCausality = CAUSALITY_TERMS.stream().anyMatch(normalized::contains);
    if (unsupportedCausality) {
      return new GuardrailDecision(safeSummary(evidence), true, "UNSUPPORTED_NEWS_CAUSALITY");
    }
    return new GuardrailDecision(modelAnswer, false, null);
  }

  private String unavailableAnswer(NewsEvidenceResponse evidence) {
    return "**결론**\n"
        + evidence.symbol()
        + "과 연결된 공용 뉴스·공식자료가 없어 현재 확인할 수 없습니다.\n\n"
        + "**한계**\nNews 탭의 공식자료 갱신 상태를 확인해주세요.";
  }

  private String safeSummary(NewsEvidenceResponse evidence) {
    StringBuilder answer =
        new StringBuilder("**결론**\n")
            .append(evidence.symbol())
            .append("과 연결된 최근 공식자료 ")
            .append(evidence.newsCount())
            .append("건을 확인했습니다.\n\n**자료**\n");
    evidence.items().forEach(
        item ->
            answer
                .append("- ")
                .append(item.title())
                .append(" · ")
                .append(item.publisher())
                .append("\n"));
    return answer
        .append("\n**한계**\n자료와 가격 변동의 직접적인 인과관계는 확인할 수 없습니다.")
        .toString();
  }

  public record GuardrailDecision(String answer, boolean replaced, String violationCode) {}
}
