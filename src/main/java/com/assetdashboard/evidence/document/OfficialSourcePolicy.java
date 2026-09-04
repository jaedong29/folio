package com.assetdashboard.evidence.document;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 공식자료 URL의 host를 명시적 화이트리스트와 비교한다. */
@Component
@RequiredArgsConstructor
public class OfficialSourcePolicy {

  private final EvidenceProperties evidenceProperties;

  /**
   * 자료 종류와 URL을 바탕으로 저장할 신뢰 등급을 결정한다.
   *
   * <p>{@code not-sec.gov.example.com} 같은 접미사 위장 도메인은 허용하지 않는다. host가 화이트리스트와
   * 정확히 같거나 {@code .whitelisted-domain}으로 끝날 때만 공식 출처다.
   */
  public EvidenceTrust resolveTrust(EvidenceSourceType sourceType, String sourceUrl) {
    return switch (sourceType) {
      case OFFICIAL ->
          isVerifiedOfficial(sourceUrl)
              ? EvidenceTrust.VERIFIED_OFFICIAL
              : EvidenceTrust.USER_ASSERTED_OFFICIAL;
      case NEWS -> EvidenceTrust.USER_ASSERTED_NEWS;
      case USER_NOTE -> EvidenceTrust.USER_PROVIDED;
    };
  }

  boolean isVerifiedOfficial(String sourceUrl) {
    if (sourceUrl == null || sourceUrl.isBlank()) {
      return false;
    }
    try {
      String host = new URI(sourceUrl).getHost();
      if (host == null) {
        return false;
      }
      String normalizedHost = host.toLowerCase(Locale.ROOT);
      return evidenceProperties.officialDomains().stream()
          .map(domain -> domain.toLowerCase(Locale.ROOT))
          .anyMatch(
              domain ->
                  normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain));
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
