package com.assetdashboard.infra.price;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 시세 API 호출에 쓰는 HTTP 클라이언트를 구성한다.
 *
 * <p>핵심은 <b>타임아웃</b>이다. connect/read 를 각 2초로 제한해 외부 API 가 느려져도 대시보드 전체가 멈추지
 * 않게 한다. 타임아웃 없는 호출 하나가 화면 전체를 인질로 잡는 것이 가장 흔한 실패 방식이다(PRD 2장 제약 2).
 */
@Configuration
public class PriceHttpConfig {

  /**
   * Yahoo Finance 는 User-Agent 를 보고 요청을 걸러낸다.
   *
   * <p>실측 결과 (같은 IP, 같은 시각):
   *
   * <pre>
   *   User-Agent 없음                 → 429 Too Many Requests
   *   전체 Chrome UA (Mozilla/5.0 …)  → 429 Too Many Requests
   *   "Mozilla/5.0"                   → 200 OK
   * </pre>
   *
   * <p>브라우저를 정교하게 흉내 낼수록 오히려 차단된다 — 진짜 Chrome 이라면 함께 왔을 쿠키·헤더가 없어 봇으로
   * 판별되는 것으로 보인다. 비공식 API 에 의존할 때 감수해야 하는 종류의 불안정성이며, 공식 API 전환의 근거이기도
   * 하다(Roadmap 7-2).
   */
  private static final String USER_AGENT = "Mozilla/5.0";

  /**
   * 타임아웃이 설정된 시세 조회 전용 RestClient 를 만든다.
   *
   * @param properties 시세 조회 설정
   * @return 시세 조회용 RestClient
   */
  @Bean
  public RestClient priceRestClient(PriceProperties properties) {
    Duration timeout = Duration.ofMillis(properties.timeoutMillis());
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(timeout);
    factory.setReadTimeout(timeout);

    return RestClient.builder()
        .requestFactory(factory)
        .defaultHeader("User-Agent", USER_AGENT)
        .defaultHeader("Accept", "application/json")
        .build();
  }
}
