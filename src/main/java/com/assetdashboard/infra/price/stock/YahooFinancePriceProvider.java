package com.assetdashboard.infra.price.stock;

import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.global.resilience.ExternalCallResilience;
import com.assetdashboard.global.resilience.ExternalSource;
import com.assetdashboard.infra.price.PriceProperties;
import com.assetdashboard.infra.price.PriceProvider;
import com.assetdashboard.infra.price.PriceProviderException;
import com.assetdashboard.infra.price.PriceProviderException.Kind;
import com.assetdashboard.infra.price.PriceQuote;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Yahoo Finance 비공식 API 로 주식 시세를 조회한다.
 *
 * <p>국내주식은 시장 접미사가 필요하다 — 코스피 {@code 000660.KS}, 코스닥 {@code .KQ}. 해외주식은 티커를
 * 그대로 쓴다({@code NVDA}).
 *
 * <p><b>알려진 리스크</b>: 비공식 API 라 SLA 가 없고 응답 구조가 예고 없이 바뀌거나 IP 단위로 차단될 수 있다
 * (PRD 8-1). 그래서 이 클래스는 실패를 예외로 던지는 것까지만 책임지고, 폴백 판단은 상위
 * {@code PriceQueryService} 가 한다. 공식/유료 API 전환은 이 클래스를 교체하는 것으로 끝난다(Roadmap 7-2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinancePriceProvider implements PriceProvider {

  private static final String CHART_URL =
      "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=1d";

  private final RestClient priceRestClient;
  private final PriceProperties properties;
  private final ExternalCallResilience resilience;

  @Override
  public boolean supports(AssetType type) {
    return type == AssetType.STOCK;
  }

  @Override
  public PriceQuote fetchPrice(String symbol) {
    if (!properties.externalEnabled()) {
      throw new PriceProviderException(
          Kind.PROVIDER_UNAVAILABLE, "외부 시세 조회가 비활성화되어 있습니다. (app.price.external-enabled=false)");
    }
    try {
      return resilience.executeRead(
          ExternalSource.YAHOO_FINANCE,
          () -> {
            JsonNode body =
                priceRestClient.get().uri(CHART_URL, symbol).retrieve().body(JsonNode.class);
            return PriceQuote.of(extractPrice(body, symbol));
          });
    } catch (PriceProviderException e) {
      throw e;
    } catch (HttpClientErrorException e) {
      // 4xx 를 전부 "잘못된 심볼"로 보면 안 된다. Yahoo 는 없는 티커에 404 를 주지만,
      // 호출이 잦으면 IP 단위로 429(Too Many Requests)를, 쿠키 정책 변경 시 401/403 을 준다.
      // 이것들은 우리 쪽 사정이지 사용자 입력 탓이 아니므로 자산 등록을 막아서는 안 된다.
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new PriceProviderException(
            Kind.SYMBOL_NOT_FOUND, "Yahoo Finance 에 존재하지 않는 심볼입니다: %s".formatted(symbol), e);
      }
      throw new PriceProviderException(
          Kind.PROVIDER_UNAVAILABLE,
          "Yahoo Finance 응답 거부(%s): %s".formatted(e.getStatusCode(), symbol),
          e);
    } catch (Exception e) {
      // 타임아웃·5xx·JSON 파싱 실패는 외부 장애로 분류한다. 사용자 입력 탓이 아니다.
      throw new PriceProviderException(
          Kind.PROVIDER_UNAVAILABLE, "Yahoo Finance 조회 실패: %s".formatted(symbol), e);
    }
  }

  /**
   * 응답 JSON 에서 현재가를 꺼낸다.
   *
   * @param body Yahoo Finance 응답
   * @param symbol 조회한 심볼 (에러 메시지용)
   * @return 현재가
   * @throws PriceProviderException 응답에 가격이 없는 경우 (존재하지 않는 심볼)
   */
  private BigDecimal extractPrice(JsonNode body, String symbol) {
    if (body == null) {
      throw new PriceProviderException(
          Kind.PROVIDER_UNAVAILABLE, "Yahoo Finance 응답이 비어 있습니다: %s".formatted(symbol));
    }
    JsonNode price =
        body.path("chart").path("result").path(0).path("meta").path("regularMarketPrice");
    if (price.isMissingNode() || price.isNull()) {
      throw new PriceProviderException(
          Kind.SYMBOL_NOT_FOUND, "Yahoo Finance 에 존재하지 않는 심볼입니다: %s".formatted(symbol));
    }
    return price.decimalValue();
  }
}
