package com.assetdashboard.evidence.agent;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** 모델 호출 전에 질문이 요구하는 근거 종류를 결정적으로 제한한다. */
@Component
public class FinancialQuestionIntentClassifier {

  private static final List<String> PRICE_TREND_TERMS =
      List.of(
          "방향성",
          "방향",
          "추세",
          "상승세",
          "하락세",
          "오름세",
          "내림세",
          "모멘텀",
          "trend",
          "momentum",
          "direction");

  private static final List<String> NEWS_TERMS =
      List.of(
          "뉴스",
          "소식",
          "공식자료",
          "공시",
          "발표",
          "릴리스",
          "업데이트",
          "news",
          "release",
          "announcement");

  public FinancialQuestionIntent classify(String question) {
    String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
    boolean asksNews = NEWS_TERMS.stream().anyMatch(normalized::contains);
    if (asksNews) {
      return FinancialQuestionIntent.SYMBOL_NEWS;
    }
    boolean asksTrend = PRICE_TREND_TERMS.stream().anyMatch(normalized::contains);
    return asksTrend
        ? FinancialQuestionIntent.PRICE_TREND
        : FinancialQuestionIntent.ASSET_CALCULATION;
  }
}
