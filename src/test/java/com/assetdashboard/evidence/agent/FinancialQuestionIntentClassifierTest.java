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
}
