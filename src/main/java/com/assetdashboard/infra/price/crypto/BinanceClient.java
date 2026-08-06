package com.assetdashboard.infra.price.crypto;

import com.assetdashboard.infra.price.PriceProperties;
import com.assetdashboard.infra.price.PriceProviderException;
import com.assetdashboard.infra.price.PriceProviderException.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Binance 에서 코인의 USDT 기준 가격을 조회한다. */
@Component
@RequiredArgsConstructor
public class BinanceClient {

  private static final String TICKER_URL = "https://api.binance.com/api/v3/ticker/price?symbol={pair}";

  /** 기준 통화 자체. USDT 를 USDT 로 환산하면 항상 1이므로 외부 호출을 하지 않는다. */
  private static final String QUOTE_ASSET = "USDT";

  private final RestClient priceRestClient;
  private final PriceProperties properties;

  /**
   * 코인의 USDT 기준 가격을 조회한다.
   *
   * @param symbol 코인 심볼 (예: {@code BTC})
   * @return USDT 기준 가격
   * @throws PriceProviderException 조회 실패 또는 존재하지 않는 심볼인 경우
   */
  public BigDecimal fetchUsdtPrice(String symbol) {
    if (!properties.externalEnabled()) {
      throw new PriceProviderException(
          Kind.PROVIDER_UNAVAILABLE, "외부 시세 조회가 비활성화되어 있습니다. (app.price.external-enabled=false)");
    }
    if (QUOTE_ASSET.equals(symbol)) {
      return BigDecimal.ONE;
    }
    String pair = symbol + QUOTE_ASSET;
    try {
      JsonNode body = priceRestClient.get().uri(TICKER_URL, pair).retrieve().body(JsonNode.class);
      JsonNode price = body == null ? null : body.path("price");
      if (price == null || price.isMissingNode() || price.isNull()) {
        throw new PriceProviderException(
            Kind.SYMBOL_NOT_FOUND, "Binance 에 존재하지 않는 페어입니다: %s".formatted(pair));
      }
      return new BigDecimal(price.asText());
    } catch (PriceProviderException e) {
      throw e;
    } catch (HttpClientErrorException e) {
      // Binance 는 없는 페어에 400(-1121 Invalid symbol)을 준다. 반면 429/418 은 레이트리밋이므로
      // 사용자 입력 오류가 아니다 — 이 둘을 섞으면 우리 쪽 장애 때문에 자산 등록이 막힌다.
      if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
        throw new PriceProviderException(
            Kind.SYMBOL_NOT_FOUND, "Binance 에 존재하지 않는 페어입니다: %s".formatted(pair), e);
      }
      throw new PriceProviderException(
          Kind.PROVIDER_UNAVAILABLE,
          "Binance 응답 거부(%s): %s".formatted(e.getStatusCode(), pair),
          e);
    } catch (Exception e) {
      throw new PriceProviderException(
          Kind.PROVIDER_UNAVAILABLE, "Binance 조회 실패: %s".formatted(pair), e);
    }
  }
}
