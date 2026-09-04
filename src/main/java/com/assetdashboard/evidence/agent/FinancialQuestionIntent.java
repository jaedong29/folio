package com.assetdashboard.evidence.agent;

import com.assetdashboard.evidence.tool.AssetEvidenceToolAdapter;
import com.assetdashboard.evidence.news.NewsEvidenceToolAdapter;
import com.assetdashboard.evidence.trend.PriceTrendEvidenceToolAdapter;

/** 질문에 답하기 위해 반드시 필요한 읽기 전용 Evidence 종류. */
public enum FinancialQuestionIntent {
  ASSET_CALCULATION(AssetEvidenceToolAdapter.TOOL_NAME),
  PRICE_TREND(PriceTrendEvidenceToolAdapter.TOOL_NAME),
  SYMBOL_NEWS(NewsEvidenceToolAdapter.TOOL_NAME);

  private final String requiredToolName;

  FinancialQuestionIntent(String requiredToolName) {
    this.requiredToolName = requiredToolName;
  }

  public String requiredToolName() {
    return requiredToolName;
  }
}
