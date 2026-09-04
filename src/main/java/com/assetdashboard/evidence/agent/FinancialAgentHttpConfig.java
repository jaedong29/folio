package com.assetdashboard.evidence.agent;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 시세 API와 분리된 타임아웃을 사용하는 LLM 전용 HTTP 클라이언트. */
@Configuration
public class FinancialAgentHttpConfig {

  @Bean
  @Qualifier("financialAgentRestClient")
  public RestClient financialAgentRestClient(FinancialAgentProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()));
    factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));
    return RestClient.builder()
        .baseUrl(properties.baseUrl())
        .requestFactory(factory)
        .defaultHeader("Accept", "application/json")
        .build();
  }
}
