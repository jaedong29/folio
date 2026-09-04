package com.assetdashboard.evidence.document;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 사용자가 직접 붙여넣은 근거 자료를 다룰 때 두 가지를 결정적으로 막는다.
 *
 * <p>(1) 원문에 섞인 지시("이전 지시 무시", "시스템 프롬프트 공개" 등)를 모델이 그대로 따라 말하는 것,
 * (2) 검증된 공식 출처(VERIFIED_OFFICIAL)가 아닌 자료를 두고 "검증된 공식 출처"라고 과장하는 것.
 */
@Component
public class SymbolEvidenceAnswerGuardrail {

  private static final List<String> INJECTION_ECHO_TERMS =
      List.of("이전 지시", "시스템 프롬프트", "다른 사용자", "ignore previous", "system prompt");

  private static final List<String> OVERCLAIM_TERMS =
      List.of("검증된 공식 출처", "공식적으로 확인", "officially confirmed", "공식 확인했다");

  public GuardrailDecision apply(String modelAnswer, SymbolEvidenceResponse evidence) {
    if (evidence.items().isEmpty()) {
      return new GuardrailDecision(
          unavailableAnswer(evidence), true, "SYMBOL_EVIDENCE_UNAVAILABLE");
    }
    String normalized = modelAnswer == null ? "" : modelAnswer.toLowerCase(Locale.ROOT);

    boolean echoesInjection =
        INJECTION_ECHO_TERMS.stream()
            .anyMatch(term -> normalized.contains(term.toLowerCase(Locale.ROOT)));
    if (echoesInjection) {
      return new GuardrailDecision(
          safeSummary(evidence), true, "SYMBOL_EVIDENCE_PROMPT_INJECTION_ECHO");
    }

    boolean hasVerifiedOfficial =
        evidence.items().stream()
            .anyMatch(item -> item.trust() == EvidenceTrust.VERIFIED_OFFICIAL);
    boolean overclaimsVerification =
        !hasVerifiedOfficial
            && OVERCLAIM_TERMS.stream()
                .anyMatch(term -> normalized.contains(term.toLowerCase(Locale.ROOT)));
    if (overclaimsVerification) {
      return new GuardrailDecision(safeSummary(evidence), true, "SYMBOL_EVIDENCE_TRUST_OVERCLAIM");
    }

    return new GuardrailDecision(modelAnswer, false, null);
  }

  private String unavailableAnswer(SymbolEvidenceResponse evidence) {
    return "**결론**\n"
        + evidence.symbol()
        + "에 등록된 근거 자료가 없어 현재 확인할 수 없습니다.\n\n"
        + "**한계**\n자산 상세 화면에서 근거 자료를 등록해주세요.";
  }

  private String safeSummary(SymbolEvidenceResponse evidence) {
    StringBuilder answer =
        new StringBuilder("**결론**\n")
            .append(evidence.symbol())
            .append("에 등록된 근거 자료 ")
            .append(evidence.documentCount())
            .append("건을 확인했습니다.\n\n**자료**\n");
    evidence.items()
        .forEach(
            item ->
                answer
                    .append("- ")
                    .append(item.title())
                    .append(" · ")
                    .append(item.trust())
                    .append("\n"));
    return answer
        .append("\n**한계**\n자료에 표시된 신뢰 등급 이상의 사실은 추가로 확정하지 않았습니다.")
        .toString();
  }

  public record GuardrailDecision(String answer, boolean replaced, String violationCode) {}
}
