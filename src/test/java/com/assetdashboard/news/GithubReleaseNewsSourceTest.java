package com.assetdashboard.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GithubReleaseNewsSourceTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void mapsPublicReleaseToVerifiedSharedEvidence() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    NewsProperties properties = new NewsProperties(true, 1000, 1000, 360, 10, 1000);
    GithubReleaseNewsSource source =
        new GithubReleaseNewsSource(builder.build(), properties, FIXED_CLOCK);
    server.expect(
            requestTo(
                "https://api.github.com/repos/ZcashFoundation/zebra/releases?per_page=10"))
        .andRespond(
            withSuccess(
                """
                [{
                  "id": 901,
                  "name": "Zebra 3.0.0",
                  "tag_name": "v3.0.0",
                  "html_url": "https://github.com/ZcashFoundation/zebra/releases/tag/v3.0.0",
                  "published_at": "2026-09-02T12:00:00Z",
                  "draft": false,
                  "body": "## Security release\\nMainnet network upgrade support."
                }]
                """,
                MediaType.APPLICATION_JSON));

    List<CollectedNewsItem> result = source.fetch();

    assertThat(result).hasSize(1);
    CollectedNewsItem item = result.get(0);
    assertThat(item.sourceKey()).isEqualTo(GithubReleaseNewsSource.SOURCE_KEY);
    assertThat(item.trust()).isEqualTo(NewsTrust.VERIFIED_OFFICIAL);
    assertThat(item.symbols()).containsExactly("ZEC");
    assertThat(item.topics()).contains("RELEASE", "SECURITY", "NETWORK_UPGRADE");
    assertThat(item.fetchedAt()).isEqualTo(Instant.parse("2026-09-03T00:00:00Z"));
    server.verify();
  }

  @Test
  void skipsDraftAndMalformedReleaseWithoutInventingDates() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GithubReleaseNewsSource source =
        new GithubReleaseNewsSource(
            builder.build(), new NewsProperties(true, 1000, 1000, 360, 10, 1000), FIXED_CLOCK);
    server.expect(
            requestTo(
                "https://api.github.com/repos/ZcashFoundation/zebra/releases?per_page=10"))
        .andRespond(
            withSuccess(
                """
                [
                  {"id":1,"draft":true,"published_at":"2026-09-01T00:00:00Z","html_url":"https://example.com/1"},
                  {"id":2,"draft":false,"published_at":null,"html_url":"https://example.com/2"}
                ]
                """,
                MediaType.APPLICATION_JSON));

    assertThat(source.fetch()).isEmpty();
    server.verify();
  }

  @Test
  void refusesExternalCollectionWhenDisabled() {
    GithubReleaseNewsSource source =
        new GithubReleaseNewsSource(
            RestClient.create(),
            new NewsProperties(false, 1000, 1000, 360, 10, 1000),
            FIXED_CLOCK);

    assertThatThrownBy(source::fetch)
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NEWS_COLLECTION_DISABLED));
  }
}
