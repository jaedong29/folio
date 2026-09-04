package com.assetdashboard.evidence.document;

import java.time.Instant;

/** 모델에는 사용자가 직접 등록한 자료의 메타데이터와 원문을 신뢰 등급과 함께 전달한다. */
public record SymbolEvidenceItem(
    Long documentId,
    EvidenceSourceType sourceType,
    EvidenceTrust trust,
    String title,
    String publisher,
    String sourceUrl,
    Instant publishedAt,
    String content) {

  private static final int MAX_CONTENT_CHARS = 3000;

  static SymbolEvidenceItem from(EvidenceDocument document) {
    String content = document.getContent();
    String bounded =
        content.length() > MAX_CONTENT_CHARS ? content.substring(0, MAX_CONTENT_CHARS) : content;
    return new SymbolEvidenceItem(
        document.getId(),
        document.getSourceType(),
        document.getTrust(),
        document.getTitle(),
        document.getPublisher(),
        document.getSourceUrl(),
        document.getPublishedAt(),
        bounded);
  }
}
