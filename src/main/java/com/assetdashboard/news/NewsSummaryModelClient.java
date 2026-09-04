package com.assetdashboard.news;

/** 공용 공식자료를 한 번만 요약하는 모델 제공자 경계. */
public interface NewsSummaryModelClient {

  NewsSummaryDraft summarize(ClaimedNewsSummary news);

  boolean isEnabled();
}
