package com.assetdashboard.infra.price.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.global.resilience.ExternalCallResilienceTestSupport;
import com.assetdashboard.infra.price.PriceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PriceHistoryQueryServiceTest {

  @Test
  void parsesBinanceDailyCloseAndReusesCache() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    PriceHistoryQueryService service =
        new PriceHistoryQueryService(
            builder.build(),
            new PriceProperties(15, 2000, true),
            ExternalCallResilienceTestSupport.create());

    server
        .expect(
            requestTo(
                "https://api.binance.com/api/v3/klines?symbol=ZECUSDT&interval=1d&limit=7"))
        .andRespond(
            withSuccess(
                """
                [
                  [1785542400000,"460","470","450","461.71","100",0,"0",1,"0","0","0"],
                  [1785628800000,"461","495","458","489.20","100",0,"0",1,"0","0","0"]
                ]
                """,
                MediaType.APPLICATION_JSON));

    PriceHistoryQuote first = service.getHistory(AssetType.CRYPTO, "ZEC");
    PriceHistoryQuote cached = service.getHistory(AssetType.CRYPTO, "ZEC");

    assertThat(first.available()).isTrue();
    assertThat(first.points()).hasSize(2);
    assertThat(first.points().get(1).price()).isEqualByComparingTo("489.20");
    assertThat(cached).isSameAs(first);
    server.verify();
  }
}
