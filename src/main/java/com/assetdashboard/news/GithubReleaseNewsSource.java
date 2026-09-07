package com.assetdashboard.news;

import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.global.resilience.ExternalCallRejectedException;
import com.assetdashboard.global.resilience.ExternalCallResilience;
import com.assetdashboard.global.resilience.ExternalSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Zcash Foundation의 공개 Zebra Release를 수집하는 첫 공식 출처 Adapter. */
@Slf4j
@Component
public class GithubReleaseNewsSource implements OfficialNewsSource {

  public static final String SOURCE_KEY = "ZCASH_ZEBRA_GITHUB";
  private static final String RELEASES_URL =
      "https://api.github.com/repos/ZcashFoundation/zebra/releases?per_page={limit}";
  private static final int MAX_CONTENT_LENGTH = 50_000;
  private static final int EXCERPT_LENGTH = 360;

  private final RestClient restClient;
  private final NewsProperties properties;
  private final Clock clock;
  private final ExternalCallResilience resilience;

  public GithubReleaseNewsSource(
      @Qualifier("newsRestClient") RestClient restClient,
      NewsProperties properties,
      Clock clock,
      ExternalCallResilience resilience) {
    this.restClient = restClient;
    this.properties = properties;
    this.clock = clock;
    this.resilience = resilience;
  }

  @Override
  public String key() {
    return SOURCE_KEY;
  }

  @Override
  public String displayName() {
    return "Zcash Foundation · Zebra Releases";
  }

  @Override
  public NewsCategory category() {
    return NewsCategory.CRYPTO;
  }

  @Override
  public Set<String> symbols() {
    return Set.of("ZEC");
  }

  @Override
  public List<CollectedNewsItem> fetch() {
    if (!properties.externalEnabled()) {
      throw new BusinessException(ErrorCode.NEWS_COLLECTION_DISABLED);
    }
    try {
      JsonNode response =
          resilience.executeRead(
              ExternalSource.GITHUB_RELEASES,
              () ->
                  restClient
                      .get()
                      .uri(RELEASES_URL, properties.maxItemsPerSource())
                      .retrieve()
                      .body(JsonNode.class));
      if (response == null || !response.isArray()) {
        throw new NewsSourceException("INVALID_GITHUB_RESPONSE");
      }
      Instant fetchedAt = Instant.now(clock);
      List<CollectedNewsItem> results = new ArrayList<>();
      for (JsonNode release : response) {
        if (release.path("draft").asBoolean(false)) {
          continue;
        }
        toCollectedItem(release, fetchedAt).ifPresent(results::add);
      }
      return List.copyOf(results);
    } catch (BusinessException | NewsSourceException e) {
      throw e;
    } catch (ExternalCallRejectedException e) {
      throw new NewsSourceException("GITHUB_CIRCUIT_OPEN", e);
    } catch (RestClientException e) {
      log.warn("GitHub release collection failed: {}", e.getClass().getSimpleName());
      throw new NewsSourceException("GITHUB_UNAVAILABLE", e);
    }
  }

  private java.util.Optional<CollectedNewsItem> toCollectedItem(
      JsonNode release, Instant fetchedAt) {
    String externalId = release.path("id").asText("").trim();
    String sourceUrl = release.path("html_url").asText("").trim();
    String publishedValue = release.path("published_at").asText("").trim();
    if (externalId.isEmpty() || sourceUrl.isEmpty() || publishedValue.isEmpty()) {
      log.info("GitHub release skipped because required metadata is missing");
      return java.util.Optional.empty();
    }
    try {
      Instant publishedAt = Instant.parse(publishedValue);
      String releaseName = release.path("name").asText("").trim();
      String tagName = release.path("tag_name").asText("").trim();
      String label = !releaseName.isEmpty() ? releaseName : tagName;
      if (label.isEmpty()) {
        label = "새 공식 Release";
      }
      String title =
          trim(
              label.toLowerCase(Locale.ROOT).startsWith("zebra")
                  ? label
                  : "Zebra " + label,
              300);
      String body = release.path("body").asText("").trim();
      String content = trim(body.isEmpty() ? title : body, MAX_CONTENT_LENGTH);
      String excerpt = trim(toPlainText(content), EXCERPT_LENGTH);
      String hashInput =
          title + "\n" + sourceUrl + "\n" + publishedAt + "\n" + normalize(content);
      return java.util.Optional.of(
          new CollectedNewsItem(
              SOURCE_KEY,
              externalId,
              NewsCategory.CRYPTO,
              NewsSourceType.OFFICIAL_RELEASE,
              NewsTrust.VERIFIED_OFFICIAL,
              title,
              "Zcash Foundation",
              sourceUrl,
              publishedAt,
              fetchedAt,
              excerpt,
              content,
              NewsContentHasher.sha256(hashInput),
              Set.of("ZEC"),
              inferTopics(title + " " + content)));
    } catch (DateTimeParseException e) {
      log.info("GitHub release skipped because published_at is invalid");
      return java.util.Optional.empty();
    }
  }

  private Set<String> inferTopics(String value) {
    String normalized = value.toLowerCase(Locale.ROOT);
    java.util.LinkedHashSet<String> topics = new java.util.LinkedHashSet<>();
    topics.add("DEVELOPMENT");
    topics.add("RELEASE");
    if (normalized.contains("security") || normalized.contains("vulnerability")) {
      topics.add("SECURITY");
    }
    if (normalized.contains("network upgrade") || normalized.contains("mainnet")) {
      topics.add("NETWORK_UPGRADE");
    }
    return Set.copyOf(topics);
  }

  private String toPlainText(String value) {
    return normalize(
        value
            .replaceAll("```[\\s\\S]*?```", " ")
            .replaceAll("!?(\\[[^]]*])\\([^)]*\\)", "$1")
            .replaceAll("[#>*_`~-]", " "));
  }

  private String normalize(String value) {
    return value.replaceAll("\\s+", " ").trim();
  }

  private String trim(String value, int maxLength) {
    String normalized = value.trim();
    return normalized.length() <= maxLength
        ? normalized
        : normalized.substring(0, maxLength - 1) + "…";
  }
}
