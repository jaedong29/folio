package com.assetdashboard.news;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 공용 뉴스 수집 비용과 외부 장애 범위를 제한하는 설정. */
@ConfigurationProperties(prefix = "app.news")
public record NewsProperties(
    boolean externalEnabled,
    int connectTimeoutMillis,
    int readTimeoutMillis,
    long refreshCooldownMinutes,
    int maxItemsPerSource,
    long workerDelayMillis) {}
