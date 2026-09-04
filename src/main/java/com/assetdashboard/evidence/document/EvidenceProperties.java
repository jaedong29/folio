package com.assetdashboard.evidence.document;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 금융 근거 문서의 공식 출처 검증 설정.
 *
 * @param officialDomains 공식 공시 출처로 인정할 도메인. 정확히 일치하거나 그 하위 도메인만 허용한다
 */
@ConfigurationProperties(prefix = "app.evidence")
public record EvidenceProperties(List<String> officialDomains) {

  public EvidenceProperties {
    officialDomains = officialDomains == null ? List.of() : List.copyOf(officialDomains);
  }
}
