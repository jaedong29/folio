package com.assetdashboard.infra.price.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.assetdashboard.global.resilience.ExternalCallResilienceTestSupport;
import com.assetdashboard.infra.price.PriceProperties;
import com.assetdashboard.infra.price.PriceProviderException;
import com.assetdashboard.infra.price.PriceProviderException.Kind;
import com.assetdashboard.infra.price.PriceQuote;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class YahooFinancePriceProviderTest {

  private static final String URL =
      "https://query1.finance.yahoo.com/v8/finance/chart/NVDA?interval=1d&range=1d";

  @Test
  void retriesServerFailureAndParsesSecondResponse() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    YahooFinancePriceProvider provider =
        new YahooFinancePriceProvider(
            builder.build(),
            new PriceProperties(15, 2000, true),
            ExternalCallResilienceTestSupport.create(10, 5, 2));
    server.expect(requestTo(URL)).andRespond(withServerError());
    server
        .expect(requestTo(URL))
        .andRespond(
            withSuccess(
                """
                {"chart":{"result":[{"meta":{"regularMarketPrice":123.45}}]}}
                """,
                MediaType.APPLICATION_JSON));

    PriceQuote quote = provider.fetchPrice("NVDA");

    assertThat(quote.price()).isEqualByComparingTo("123.45");
    server.verify();
  }

  @Test
  void doesNotRetryNotFoundAndPreservesInvalidSymbolClassification() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    YahooFinancePriceProvider provider =
        new YahooFinancePriceProvider(
            builder.build(),
            new PriceProperties(15, 2000, true),
            ExternalCallResilienceTestSupport.create(10, 5, 2));
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> provider.fetchPrice("NVDA"))
        .isInstanceOfSatisfying(
            PriceProviderException.class,
            error -> assertThat(error.getKind()).isEqualTo(Kind.SYMBOL_NOT_FOUND));
    server.verify();
  }
}
