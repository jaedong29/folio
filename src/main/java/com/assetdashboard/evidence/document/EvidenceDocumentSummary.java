package com.assetdashboard.evidence.document;

import java.time.Instant;
import java.time.LocalDateTime;

/** 목록에서 원문 전체를 반복 전송하지 않는 근거 문서 요약. */
public record EvidenceDocumentSummary(
    Long id,
    String symbol,
    EvidenceSourceType sourceType,
    EvidenceTrust trust,
    String title,
    String publisher,
    String sourceUrl,
    Instant publishedAt,
    LocalDateTime collectedAt) {

  public static EvidenceDocumentSummary from(EvidenceDocument document) {
    return new EvidenceDocumentSummary(
        document.getId(),
        document.getSymbol(),
        document.getSourceType(),
        document.getTrust(),
        document.getTitle(),
        document.getPublisher(),
        document.getSourceUrl(),
        document.getPublishedAt(),
        document.getCreatedAt());
  }
}
