package com.assetdashboard.evidence.news;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.time.Instant;
import java.util.List;

/** 등록 자산의 symbol과 공용 뉴스 저장소를 읽기 전용으로 연결한 Tool 응답. */
public record NewsEvidenceResponse(
    String traceId,
    Long assetId,
    String symbol,
    int newsCount,
    EvidenceConclusion conclusion,
    Instant asOf,
    List<NewsEvidenceItem> items,
    List<String> warnings) {

  public NewsEvidenceResponse {
    items = List.copyOf(items);
    warnings = List.copyOf(warnings);
  }
}
