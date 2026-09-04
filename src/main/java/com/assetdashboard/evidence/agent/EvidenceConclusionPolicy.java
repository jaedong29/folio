package com.assetdashboard.evidence.agent;

import com.assetdashboard.evidence.calculation.AssetEvidenceResponse;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.util.Collection;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** LLM 텍스트가 아니라 구조화된 Tool 결과만으로 최종 확인 수준을 결정한다. */
@Component
public class EvidenceConclusionPolicy {

  /** 계산 Evidence 서비스가 판정한 결론을 손실 없이 전달한다. */
  public EvidenceConclusion fromAssetEvidence(AssetEvidenceResponse response) {
    Objects.requireNonNull(response, "Asset Evidence 응답이 필요합니다.");
    return Objects.requireNonNull(response.conclusion(), "Asset Evidence 결론이 필요합니다.");
  }

  /** 질문에 필요해 실제 호출한 Tool 중 가장 보수적인 결론을 사용한다. */
  public EvidenceConclusion combine(Collection<GroundedToolResult> toolResults) {
    if (toolResults == null || toolResults.isEmpty()) {
      return EvidenceConclusion.UNAVAILABLE;
    }
    if (toolResults.stream()
        .anyMatch(result -> result.conclusion() == EvidenceConclusion.UNAVAILABLE)) {
      return EvidenceConclusion.UNAVAILABLE;
    }
    if (toolResults.stream()
        .anyMatch(result -> result.conclusion() == EvidenceConclusion.PARTIAL)) {
      return EvidenceConclusion.PARTIAL;
    }
    return EvidenceConclusion.CONFIRMED;
  }
}
