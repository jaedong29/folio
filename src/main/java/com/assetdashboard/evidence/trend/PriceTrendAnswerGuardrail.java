package com.assetdashboard.evidence.trend;

import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** 가격 방향 답변이 Tool에 없는 포트폴리오 손익이나 인과를 근거로 삼지 못하게 한다. */
@Component
public class PriceTrendAnswerGuardrail {

  public GuardrailDecision apply(String modelAnswer, PriceTrendEvidenceResponse evidence) {
    if (evidence.direction() == PriceTrendDirection.UNAVAILABLE) {
      return new GuardrailDecision(unavailableAnswer(), true, "PRICE_HISTORY_MISSING");
    }

    String normalized = modelAnswer == null ? "" : modelAnswer.toLowerCase(Locale.ROOT);
    if (containsPortfolioReturnEvidence(normalized)) {
      return new GuardrailDecision(
          deterministicAnswer(evidence), true, "PORTFOLIO_RETURN_USED_AS_TREND");
    }
    if (containsUnsupportedCausality(normalized)) {
      return new GuardrailDecision(
          deterministicAnswer(evidence), true, "UNSUPPORTED_TREND_CAUSALITY");
    }
    if (contradictsDirection(normalized, evidence.direction())) {
      return new GuardrailDecision(
          deterministicAnswer(evidence), true, "TREND_DIRECTION_CONTRADICTION");
    }
    return new GuardrailDecision(modelAnswer, false, null);
  }

  private boolean containsPortfolioReturnEvidence(String answer) {
    return answer.contains("평가손익")
        || answer.contains("평단")
        || answer.contains("평균 매수가")
        || answer.contains("unrealized pnl");
  }

  private boolean containsUnsupportedCausality(String answer) {
    return answer.contains("뉴스 때문에")
        || answer.contains("직접적인 원인")
        || answer.contains("이 사건 때문에");
  }

  private boolean contradictsDirection(String answer, PriceTrendDirection direction) {
    return switch (direction) {
      case UP -> answer.contains("하락세") || answer.contains("하락 방향");
      case DOWN -> answer.contains("상승세") || answer.contains("상승 방향");
      case FLAT ->
          answer.contains("상승세")
              || answer.contains("하락세")
              || answer.contains("상승 방향")
              || answer.contains("하락 방향");
      case UNAVAILABLE -> true;
    };
  }

  private String deterministicAnswer(PriceTrendEvidenceResponse evidence) {
    String direction =
        switch (evidence.direction()) {
          case UP -> "상승 방향";
          case DOWN -> "하락 방향";
          case FLAT -> "보합 범위";
          case UNAVAILABLE -> "확인 불가";
        };
    String staleNotice = evidence.stale() ? " 다만 가격 이력이 오래되어 일부 확인 수준입니다." : "";
    return "최근 %d개 일별 가격 기준으로 %s에서 %s로 변했고, 변화율은 %s%%입니다. "
            .formatted(
                evidence.pointCount(),
                plain(evidence.startPrice()),
                plain(evidence.endPrice()),
                plain(evidence.returnRatePercent()))
        + "서비스 규칙(변화율이 +2% 초과면 UP, -2% 미만이면 DOWN)에 따른 판정은 "
        + direction
        + "입니다. 평가손익이나 뉴스 인과는 방향 근거로 사용하지 않았습니다."
        + staleNotice;
  }

  private String unavailableAnswer() {
    return "최근 가격 방향을 판단할 가격 이력이 부족해 확인할 수 없습니다. "
        + "현재 평가손익률은 평균 매수가 대비 결과이며 가격 추세의 근거로 사용하지 않았습니다.";
  }

  private String plain(BigDecimal value) {
    return value == null ? "확인 불가" : value.stripTrailingZeros().toPlainString();
  }

  public record GuardrailDecision(String answer, boolean replaced, String violationCode) {}
}
