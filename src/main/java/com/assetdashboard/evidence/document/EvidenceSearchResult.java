package com.assetdashboard.evidence.document;

import java.time.Instant;

/** 향후 RAG Tool 계약으로 그대로 확장할 수 있는 symbol 문서 검색 결과. */
public record EvidenceSearchResult(
    Long documentId,
    String symbol,
    EvidenceSourceType sourceType,
    EvidenceTrust trust,
    String title,
    String publisher,
    String sourceUrl,
    Instant publishedAt,
    int relevanceScore,
    String snippet,
    boolean untrustedContent) {}
