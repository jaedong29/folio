package com.assetdashboard.evidence.document;

import java.time.Instant;
import java.time.LocalDateTime;

/** 원문을 포함한 근거 문서 단건 응답. */
public record EvidenceDocumentResponse(
    Long id,
    Long assetId,
    String symbol,
    EvidenceSourceType sourceType,
    EvidenceTrust trust,
    String title,
    String publisher,
    String sourceUrl,
    Instant publishedAt,
    LocalDateTime collectedAt,
    String content,
    boolean untrustedContent) {

  public static EvidenceDocumentResponse from(EvidenceDocument document) {
    return new EvidenceDocumentResponse(
        document.getId(),
        document.getAssetId(),
        document.getSymbol(),
        document.getSourceType(),
        document.getTrust(),
        document.getTitle(),
        document.getPublisher(),
        document.getSourceUrl(),
        document.getPublishedAt(),
        document.getCreatedAt(),
        document.getContent(),
        true);
  }
}
