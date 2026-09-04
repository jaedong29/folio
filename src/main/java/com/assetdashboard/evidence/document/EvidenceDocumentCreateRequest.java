package com.assetdashboard.evidence.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** symbol 근거 자료 수동 등록 요청. */
public record EvidenceDocumentCreateRequest(
    @NotNull EvidenceSourceType sourceType,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 120) String publisher,
    @Size(max = 2048) String sourceUrl,
    Instant publishedAt,
    @NotBlank @Size(max = 50_000) String content) {}
