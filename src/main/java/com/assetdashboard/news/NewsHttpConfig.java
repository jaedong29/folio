package com.assetdashboard.news;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class NewsHttpConfig {

  @Bean
  @Qualifier("newsRestClient")
  public RestClient newsRestClient(NewsProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()));
    factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));
    return RestClient.builder()
        .requestFactory(factory)
        .defaultHeader("User-Agent", "folio-financial-evidence-agent")
        .defaultHeader("Accept", "application/vnd.github+json")
        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
        .build();
  }
}
