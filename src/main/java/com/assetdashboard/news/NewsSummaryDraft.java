package com.assetdashboard.news;

/** 모델 응답에서 검증을 마친 뒤 저장할 한국어 요약 후보. */
public record NewsSummaryDraft(
    String summaryKo,
    String significanceKo,
    String model,
    long latencyMs,
    long inputTokens,
    long outputTokens) {}
