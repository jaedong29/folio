package com.assetdashboard.news;

import java.time.Instant;
import java.util.Set;

public record NewsSourceStatusResponse(
    String sourceKey,
    String displayName,
    NewsCategory category,
    Set<String> symbols,
    NewsRefreshStatus lastStatus,
    Instant lastCompletedAt,
    String lastErrorCode) {}
