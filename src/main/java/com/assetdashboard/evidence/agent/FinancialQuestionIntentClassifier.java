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
          "발표",
          "릴리스",
          "업데이트",
          "news",
          "release",
          "announcement");

  // "등록된"/"등록한" 같은 사용자 등록 신호나 알려진 공시 시스템 이름은 News(자동 수집 공용 저장소)가 아니라
  // 사용자가 직접 붙여넣은 symbol 근거 자료를 가리킨다. NEWS_TERMS와 겹치는 "공시" 같은 단어가 있어
  // 이 목록을 먼저 검사한다.
  private static final List<String> SYMBOL_EVIDENCE_TERMS =
      List.of("등록된", "등록한", "등록해", "제출한", "붙여넣은", "dart", "kind", "sec", "filing", "공시");

  public FinancialQuestionIntent classify(String question) {
    String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
    boolean asksSymbolEvidence = SYMBOL_EVIDENCE_TERMS.stream().anyMatch(normalized::contains);
    if (asksSymbolEvidence) {
      return FinancialQuestionIntent.SYMBOL_EVIDENCE;
    }
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
