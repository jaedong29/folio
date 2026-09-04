package com.assetdashboard.evidence.document;

import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.time.Instant;
import java.util.List;

/** 등록 자산의 symbol과 사용자가 직접 연결한 근거 자료를 읽기 전용으로 이은 Tool 응답. */
public record SymbolEvidenceResponse(
    String traceId,
    Long assetId,
    String symbol,
    int documentCount,
    EvidenceConclusion conclusion,
    Instant asOf,
    List<SymbolEvidenceItem> items,
    List<String> warnings) {

  public SymbolEvidenceResponse {
    items = List.copyOf(items);
    warnings = List.copyOf(warnings);
  }
}
