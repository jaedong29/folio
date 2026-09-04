package com.assetdashboard.evidence.document;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 등록된 symbol에 기사·공식자료·사용자 메모를 연결하고 검색한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceDocumentService {

  private static final int SEARCH_LIMIT = 10;
  private static final int SNIPPET_RADIUS = 90;

  private final AssetService assetService;
  private final EvidenceDocumentRepository evidenceDocumentRepository;
  private final OfficialSourcePolicy officialSourcePolicy;

  /** 사용자가 붙여넣은 외부 자료를 등록한다. 원문 안의 명령은 신뢰하지 않는 데이터로만 저장한다. */
  @Transactional
  public EvidenceDocumentResponse create(
      Long userId, Long assetId, EvidenceDocumentCreateRequest request) {
    Asset asset = requireInvestmentAsset(userId, assetId);
    validateProvenance(request);

    String content = request.content().trim();
    String sourceUrl = normalizeUrl(request.sourceUrl());
    String hash = sha256(normalizeForHash(content));
    if (evidenceDocumentRepository.existsByUserIdAndAssetIdAndContentHash(
        userId, assetId, hash)) {
      throw new BusinessException(ErrorCode.DUPLICATE_EVIDENCE_DOCUMENT);
    }

    EvidenceDocument document =
        EvidenceDocument.create(
            userId,
            assetId,
            asset.getSymbol(),
            request.sourceType(),
            officialSourcePolicy.resolveTrust(request.sourceType(), sourceUrl),
            request.title().trim(),
            trimToNull(request.publisher()),
            sourceUrl,
            request.publishedAt(),
            content,
            hash);
    EvidenceDocument saved = evidenceDocumentRepository.save(document);
    log.info(
        "[Evidence] 문서 등록 documentId={} assetId={} userId={} sourceType={}",
        saved.getId(),
        assetId,
        userId,
        saved.getSourceType());
    return EvidenceDocumentResponse.from(saved);
  }

  /** 자산에 등록된 자료의 메타데이터 목록을 반환한다. */
  @Transactional(readOnly = true)
  public List<EvidenceDocumentSummary> getDocuments(Long userId, Long assetId) {
    requireInvestmentAsset(userId, assetId);
    return evidenceDocumentRepository
        .findAllByUserIdAndAssetIdOrderByPublishedAtDescCreatedAtDesc(userId, assetId)
        .stream()
        .map(EvidenceDocumentSummary::from)
        .toList();
  }

  /** 원문 한 건을 조회한다. 타인의 문서는 없는 문서처럼 처리한다. */
  @Transactional(readOnly = true)
  public EvidenceDocumentResponse getDocument(Long userId, Long assetId, Long documentId) {
    requireInvestmentAsset(userId, assetId);
    return EvidenceDocumentResponse.from(getOwnedDocument(userId, assetId, documentId));
  }

  /**
   * 임베딩을 도입하기 전 검증 가능한 1차 키워드 검색.
   *
   * <p>검색 결과는 신뢰하지 않는 원문 조각이며, 향후 LLM 프롬프트에서는 지시문이 아닌 인용 데이터로 격리해야 한다.
   */
  @Transactional(readOnly = true)
  public List<EvidenceSearchResult> search(
      Long userId, Long assetId, String rawQuery) {
    Asset asset = requireInvestmentAsset(userId, assetId);
    String query = rawQuery == null ? "" : rawQuery.trim();
    if (query.length() < 2 || query.length() > 200) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "검색어는 2자 이상 200자 이하로 입력해주세요.");
    }

    List<String> tokens =
        Arrays.stream(query.toLowerCase(Locale.ROOT).split("[\\s,]+"))
            .filter(token -> token.length() >= 2)
            .distinct()
            .toList();
    if (tokens.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "검색 가능한 단어를 입력해주세요.");
    }

    return evidenceDocumentRepository
        .findAllByUserIdAndAssetIdOrderByPublishedAtDescCreatedAtDesc(userId, assetId)
        .stream()
        .map(document -> toScoredDocument(asset.getSymbol(), document, tokens))
        .filter(scored -> scored.score() > 0)
        .sorted(
            Comparator.comparingInt(ScoredDocument::score)
                .reversed()
                .thenComparing(
                    scored -> scored.document().getPublishedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(SEARCH_LIMIT)
        .map(this::toSearchResult)
        .toList();
  }

  /** 사용자가 직접 등록한 자료 한 건을 삭제한다. */
  @Transactional
  public void delete(Long userId, Long assetId, Long documentId) {
    requireInvestmentAsset(userId, assetId);
    EvidenceDocument document = getOwnedDocument(userId, assetId, documentId);
    evidenceDocumentRepository.delete(document);
    log.info(
        "[Evidence] 문서 삭제 documentId={} assetId={} userId={}",
        documentId,
        assetId,
        userId);
  }

  private Asset requireInvestmentAsset(Long userId, Long assetId) {
    Asset asset = assetService.getOwnedAsset(userId, assetId);
    if (!asset.getType().isInvestment()) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "공식자료와 뉴스는 STOCK/CRYPTO 자산에만 등록할 수 있습니다.");
    }
    return asset;
  }

  private EvidenceDocument getOwnedDocument(Long userId, Long assetId, Long documentId) {
    return evidenceDocumentRepository
        .findByIdAndUserIdAndAssetId(documentId, userId, assetId)
        .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_DOCUMENT_NOT_FOUND));
  }

  private void validateProvenance(EvidenceDocumentCreateRequest request) {
    if (request.sourceType() == EvidenceSourceType.USER_NOTE) {
      return;
    }
    if (request.publisher() == null || request.publisher().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "공식자료와 뉴스에는 발행처가 필요합니다.");
    }
    if (request.sourceUrl() == null || request.sourceUrl().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "공식자료와 뉴스에는 원문 URL이 필요합니다.");
    }
    if (request.publishedAt() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "공식자료와 뉴스에는 발표 시각이 필요합니다.");
    }
  }

  private String normalizeUrl(String rawUrl) {
    if (rawUrl == null || rawUrl.isBlank()) {
      return null;
    }
    String value = rawUrl.trim();
    try {
      URI uri = new URI(value);
      String scheme = uri.getScheme();
      if (uri.getHost() == null
          || scheme == null
          || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
        throw new BusinessException(ErrorCode.INVALID_INPUT, "원문 URL은 http 또는 https 주소여야 합니다.");
      }
      return uri.normalize().toString();
    } catch (URISyntaxException e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "원문 URL 형식이 올바르지 않습니다.");
    }
  }

  private ScoredDocument toScoredDocument(
      String symbol, EvidenceDocument document, List<String> tokens) {
    String title = document.getTitle().toLowerCase(Locale.ROOT);
    String publisher =
        document.getPublisher() == null
            ? ""
            : document.getPublisher().toLowerCase(Locale.ROOT);
    String content = document.getContent().toLowerCase(Locale.ROOT);
    int score = 0;
    for (String token : tokens) {
      if (title.contains(token)) {
        score += 5;
      }
      if (publisher.contains(token)) {
        score += 3;
      }
      if (content.contains(token)) {
        score += 1;
      }
      if (symbol.toLowerCase(Locale.ROOT).equals(token)) {
        score += 2;
      }
    }
    return new ScoredDocument(document, score, firstMatchingToken(content, tokens));
  }

  private EvidenceSearchResult toSearchResult(ScoredDocument scored) {
    EvidenceDocument document = scored.document();
    return new EvidenceSearchResult(
        document.getId(),
        document.getSymbol(),
        document.getSourceType(),
        document.getTrust(),
        document.getTitle(),
        document.getPublisher(),
        document.getSourceUrl(),
        document.getPublishedAt(),
        scored.score(),
        snippet(document.getContent(), scored.match()),
        true);
  }

  private String firstMatchingToken(String content, List<String> tokens) {
    return tokens.stream().filter(content::contains).findFirst().orElse(tokens.get(0));
  }

  private String snippet(String content, String token) {
    String normalized = content.replaceAll("\\s+", " ").trim();
    int index = normalized.toLowerCase(Locale.ROOT).indexOf(token);
    if (index < 0) {
      index = 0;
    }
    int start = Math.max(0, index - SNIPPET_RADIUS);
    int end = Math.min(normalized.length(), index + token.length() + SNIPPET_RADIUS);
    return (start > 0 ? "…" : "")
        + normalized.substring(start, end)
        + (end < normalized.length() ? "…" : "");
  }

  private String normalizeForHash(String content) {
    return content.replaceAll("\\s+", " ").trim();
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM이 SHA-256을 지원하지 않습니다.", e);
    }
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private record ScoredDocument(EvidenceDocument document, int score, String match) {}
}
