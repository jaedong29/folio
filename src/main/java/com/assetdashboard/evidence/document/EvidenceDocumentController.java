package com.assetdashboard.evidence.document;

import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 등록된 자산 symbol에 사용자가 직접 자료를 연결하는 API. */
@Tag(name = "Symbol Evidence", description = "등록 자산의 공식자료·뉴스·메모 수집과 검색")
@RestController
@RequestMapping("/api/assets/{assetId}/evidence-documents")
@RequiredArgsConstructor
public class EvidenceDocumentController {

  private final EvidenceDocumentService evidenceDocumentService;

  @Operation(summary = "symbol 근거 자료 등록", description = "원문은 실행 명령이 아닌 신뢰하지 않는 인용 데이터로 저장한다.")
  @PostMapping
  public ResponseEntity<EvidenceDocumentResponse> create(
      @CurrentUserId Long userId,
      @PathVariable Long assetId,
      @Valid @RequestBody EvidenceDocumentCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(evidenceDocumentService.create(userId, assetId, request));
  }

  @Operation(summary = "symbol 근거 자료 목록")
  @GetMapping
  public ResponseEntity<List<EvidenceDocumentSummary>> getDocuments(
      @CurrentUserId Long userId, @PathVariable Long assetId) {
    return ResponseEntity.ok(evidenceDocumentService.getDocuments(userId, assetId));
  }

  @Operation(summary = "symbol 근거 자료 단건 조회")
  @GetMapping("/{documentId}")
  public ResponseEntity<EvidenceDocumentResponse> getDocument(
      @CurrentUserId Long userId,
      @PathVariable Long assetId,
      @PathVariable Long documentId) {
    return ResponseEntity.ok(
        evidenceDocumentService.getDocument(userId, assetId, documentId));
  }

  @Operation(summary = "symbol 근거 자료 검색", description = "1차 MVP의 결정적 키워드 검색. 이후 임베딩 RAG로 교체한다.")
  @GetMapping("/search")
  public ResponseEntity<List<EvidenceSearchResult>> search(
      @CurrentUserId Long userId,
      @PathVariable Long assetId,
      @RequestParam String query) {
    return ResponseEntity.ok(evidenceDocumentService.search(userId, assetId, query));
  }

  @Operation(summary = "symbol 근거 자료 삭제")
  @DeleteMapping("/{documentId}")
  public ResponseEntity<Void> delete(
      @CurrentUserId Long userId,
      @PathVariable Long assetId,
      @PathVariable Long documentId) {
    evidenceDocumentService.delete(userId, assetId, documentId);
    return ResponseEntity.noContent().build();
  }
}
