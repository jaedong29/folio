package com.assetdashboard.infra.price.fx;

import com.assetdashboard.infra.price.PriceProperties;
import com.assetdashboard.infra.price.PriceProviderException;
import com.assetdashboard.infra.price.PriceProviderException.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Yahoo Finance의 USD/KRW 환율 심볼({@code KRW=X})을 조회한다. */
@Component
@RequiredArgsConstructor
public class YahooFinanceExchangeRateClient {

  private static final String USD_KRW_URL =
      "https://query1.finance.yahoo.com/v8/finance/chart/KRW=X?interval=1d&range=1d";

  private final RestClient priceRestClient;
  private final PriceProperties properties;

  /**
   * 현재 USD/KRW 환율을 조회한다.
   *
   * <p>Yahoo Finance는 비공식 조회처이므로 호출 실패는 상위 캐시 서비스가 마지막 성공값으로 처리한다.
   *
   * @return 원/USD 환율
   * @throws PriceProviderException 조회 실패 또는 응답에 가격이 없는 경우
   */
  public BigDecimal fetchKrwPerUsd() {
    if (!properties.externalEnabled()) {
      throw new PriceProviderException(
          Kind.PROVIDER_UNAVAILABLE,
          "외부 시세 조회가 비활성화되어 있습니다. (app.price.external-enabled=false)");
    }
    try {
      JsonNode body = priceRestClient.get().uri(USD_KRW_URL).retrieve().body(JsonNode.class);
      JsonNode price =
          body == null
              ? null
              : body.path("chart").path("result").path(0).path("meta").path("regularMarketPrice");
      if (price == null || price.isMissingNode() || price.isNull()) {
        throw new PriceProviderException(
            Kind.PROVIDER_UNAVAILABLE, "Yahoo Finance USD/KRW 응답에 가격이 없습니다.");
      }
      return price.decimalValue();
    } catch (PriceProviderException e) {
      throw e;
    } catch (Exception e) {
      throw new PriceProviderException(Kind.PROVIDER_UNAVAILABLE, "Yahoo Finance USD/KRW 조회 실패", e);
    }
  }
}
