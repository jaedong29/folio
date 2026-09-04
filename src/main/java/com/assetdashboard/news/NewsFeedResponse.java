package com.assetdashboard.news;

import java.time.Instant;
import java.util.List;

public record NewsFeedResponse(
    NewsScope scope,
    NewsCategory category,
    List<String> portfolioSymbols,
    List<NewsItemResponse> items,
    Instant generatedAt) {}
