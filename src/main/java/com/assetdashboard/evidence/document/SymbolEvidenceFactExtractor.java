package com.assetdashboard.evidence.document;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** LLM 문장이 아니라 저장된 근거 문서 메타데이터에서만 채점 fact를 만든다. */
@Component
public class SymbolEvidenceFactExtractor {

  public Set<String> extract(SymbolEvidenceResponse response) {
    LinkedHashSet<String> facts = new LinkedHashSet<>();
    facts.add("symbol");
    facts.add("documentCount");
    if (response.items().isEmpty()) {
      facts.add("EVIDENCE_UNAVAILABLE");
      facts.add("documentCount=0");
      return Set.copyOf(facts);
    }
    facts.add("EVIDENCE_AVAILABLE");
    facts.add("untrustedContent=true");
    response.items()
        .forEach(
            item -> {
              facts.add(item.trust().name());
              facts.add("documentId");
              facts.add("sourceUrl");
              facts.add("publishedAt");
            });
    return Set.copyOf(facts);
  }
}
