package com.assetdashboard.evidence.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FinancialQuestionIntentClassifierTest {

  private final FinancialQuestionIntentClassifier classifier =
      new FinancialQuestionIntentClassifier();

  @Test
  void routesDirectionQuestionToPriceTrendInsteadOfPortfolioProfitEvidence() {
    assertThat(classifier.classify("현재 symbol의 방향성에 대해 근거를 좀 줘"))
        .isEqualTo(FinancialQuestionIntent.PRICE_TREND);
  }

  @Test
  void keepsValuationQuestionOnAssetCalculationEvidence() {
    assertThat(classifier.classify("현재 평가금액과 환율 계산 근거를 알려줘"))
        .isEqualTo(FinancialQuestionIntent.ASSET_CALCULATION);
  }

  @Test
  void routesOfficialUpdateQuestionToSharedNewsEvidence() {
    assertThat(classifier.classify("ZEC의 최신 공식 개발 소식을 알려줘"))
        .isEqualTo(FinancialQuestionIntent.SYMBOL_NEWS);
  }

  @Test
  void routesUserRegisteredDocumentQuestionToSymbolEvidenceNotSharedNews() {
    assertThat(classifier.classify("사용자가 공식자료로 등록한 Apple 문서를 근거로 요약해줘"))
        .isEqualTo(FinancialQuestionIntent.SYMBOL_EVIDENCE);
  }

  @Test
  void routesKnownFilingSystemNamesToSymbolEvidence() {
    assertThat(classifier.classify("AAPL과 연결된 SEC filing을 보여줘"))
        .isEqualTo(FinancialQuestionIntent.SYMBOL_EVIDENCE);
    assertThat(classifier.classify("KIND에 등록된 IR 자료를 찾아줘"))
        .isEqualTo(FinancialQuestionIntent.SYMBOL_EVIDENCE);
    assertThat(classifier.classify("등록된 DART 공시의 핵심 내용을 알려줘"))
        .isEqualTo(FinancialQuestionIntent.SYMBOL_EVIDENCE);
  }
}
