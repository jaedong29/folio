package com.assetdashboard.evidence.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** OpenAI 호환 금융 Agent 모델 연결 설정. API 키는 환경변수로만 주입한다. */
@ConfigurationProperties(prefix = "app.ai")
public record FinancialAgentProperties(
    boolean enabled,
    String baseUrl,
    String apiKey,
    String model,
    int connectTimeoutMillis,
    int readTimeoutMillis,
    boolean strictToolSelection) {}
