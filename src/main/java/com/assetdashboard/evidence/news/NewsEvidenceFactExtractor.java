package com.assetdashboard.evidence.news;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** LLM 문장이 아니라 저장된 뉴스 메타데이터에서만 채점 fact를 만든다. */
@Component
public class NewsEvidenceFactExtractor {

  public Set<String> extract(NewsEvidenceResponse response) {
    LinkedHashSet<String> facts = new LinkedHashSet<>();
    facts.add("symbol");
    facts.add("newsCount");
    if (response.items().isEmpty()) {
      facts.add("NEWS_UNAVAILABLE");
      facts.add("newsCount=0");
      return Set.copyOf(facts);
    }
    facts.add("NEWS_AVAILABLE");
    facts.add("untrustedContent=true");
    response.items().forEach(
        item -> {
          facts.add(item.trust().name());
          facts.add("newsId");
          facts.add("sourceUrl");
          facts.add("publishedAt");
        });
    return Set.copyOf(facts);
  }
}
