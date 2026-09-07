package com.assetdashboard.infra.price.crypto;

import com.assetdashboard.global.resilience.ExternalCallResilience;
import com.assetdashboard.global.resilience.ExternalSource;
import com.assetdashboard.infra.price.PriceProperties;
import com.assetdashboard.infra.price.PriceProviderException;
import com.assetdashboard.infra.price.PriceProviderException.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Upbit 의 KRW-USDT 마켓 가격을 원/USDT 환율로 사용한다.
 *
 * <p>은행 고시환율이 아니라 <b>거래소에서 실제로 체결되는 가격</b>이라는 점이 중요하다. 코인 자산의 원화 가치를
 * 따질 때는 이쪽이 실제 정산 가격에 가깝다.
 */
@Component
@RequiredArgsConstructor
public class UpbitExchangeRateClient {

  private static final String TICKER_URL = "https://api.upbit.com/v1/ticker?markets=KRW-USDT";

  private final RestClient priceRestClient;
  private final PriceProperties properties;
  private final ExternalCallResilience resilience;

  /**
   * KRW-USDT 환율을 조회한다.
   *
   * @return 원/USDT 환율
   * @throws PriceProviderException 조회에 실패한 경우
   */
  public BigDecimal fetchKrwPerUsdt() {
    if (!properties.externalEnabled()) {
      throw new PriceProviderException(
          Kind.PROVIDER_UNAVAILABLE, "외부 시세 조회가 비활성화되어 있습니다. (app.price.external-enabled=false)");
    }
    try {
      return resilience.executeRead(
          ExternalSource.UPBIT,
          () -> {
            JsonNode body =
                priceRestClient.get().uri(TICKER_URL).retrieve().body(JsonNode.class);
            JsonNode price = body == null ? null : body.path(0).path("trade_price");
            if (price == null || price.isMissingNode() || price.isNull()) {
              throw new PriceProviderException(
                  Kind.PROVIDER_UNAVAILABLE, "Upbit KRW-USDT 응답에 가격이 없습니다.");
            }
            return price.decimalValue();
          });
    } catch (PriceProviderException e) {
      throw e;
    } catch (Exception e) {
      throw new PriceProviderException(Kind.PROVIDER_UNAVAILABLE, "Upbit 환율 조회 실패", e);
    }
  }
}
