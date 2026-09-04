package com.assetdashboard.evidence.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvidenceDocumentServiceTest {

  @Mock private AssetService assetService;
  @Mock private EvidenceDocumentRepository evidenceDocumentRepository;

  private EvidenceDocumentService evidenceDocumentService;
  private Asset stock;

  @BeforeEach
  void setUp() {
    evidenceDocumentService =
        new EvidenceDocumentService(
            assetService,
            evidenceDocumentRepository,
            new OfficialSourcePolicy(
                new EvidenceProperties(
                    List.of("dart.fss.or.kr", "kind.krx.co.kr", "sec.gov"))));
    stock = Asset.create(7L, AssetType.STOCK, "AAPL", "애플", "USD");
    when(assetService.getOwnedAsset(7L, 11L)).thenReturn(stock);
  }

  @Test
  void promotesWhitelistedDartUrlToVerifiedOfficial() {
    EvidenceDocumentCreateRequest request =
        new EvidenceDocumentCreateRequest(
            EvidenceSourceType.OFFICIAL,
            "사업보고서",
            "DART",
            "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=1",
            Instant.parse("2026-09-01T00:00:00Z"),
            "공시 원문");
    when(evidenceDocumentRepository.existsByUserIdAndAssetIdAndContentHash(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq(11L),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(false);
    when(evidenceDocumentRepository.save(any(EvidenceDocument.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    EvidenceDocumentResponse response = evidenceDocumentService.create(7L, 11L, request);

    assertThat(response.trust()).isEqualTo(EvidenceTrust.VERIFIED_OFFICIAL);
  }

  @Test
  void storesOfficialDocumentAsUserAssertedAndUntrustedContent() {
    EvidenceDocumentCreateRequest request = officialRequest("신제품 매출 전망을 발표했다.");
    when(evidenceDocumentRepository.existsByUserIdAndAssetIdAndContentHash(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq(11L),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(false);
    when(evidenceDocumentRepository.save(any(EvidenceDocument.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    EvidenceDocumentResponse response = evidenceDocumentService.create(7L, 11L, request);

    ArgumentCaptor<EvidenceDocument> captor = ArgumentCaptor.forClass(EvidenceDocument.class);
    verify(evidenceDocumentRepository).save(captor.capture());
    assertThat(captor.getValue().getSymbol()).isEqualTo("AAPL");
    assertThat(captor.getValue().getTrust()).isEqualTo(EvidenceTrust.USER_ASSERTED_OFFICIAL);
    assertThat(response.untrustedContent()).isTrue();
  }

  @Test
  void rejectsDuplicateContentEvenWhenWhitespaceDiffers() {
    when(evidenceDocumentRepository.existsByUserIdAndAssetIdAndContentHash(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq(11L),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                evidenceDocumentService.create(
                    7L, 11L, officialRequest("신제품   매출\n전망을 발표했다.")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_EVIDENCE_DOCUMENT));
  }

  @Test
  void officialDocumentRequiresPublisherUrlAndPublishedTime() {
    EvidenceDocumentCreateRequest request =
        new EvidenceDocumentCreateRequest(
            EvidenceSourceType.OFFICIAL, "제목", null, null, null, "본문");

    assertThatThrownBy(() -> evidenceDocumentService.create(7L, 11L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
  }

  @Test
  void rejectsDocumentsForCashAssets() {
    Asset cash = Asset.create(7L, AssetType.CASH, "USD", "달러", "USD");
    when(assetService.getOwnedAsset(7L, 11L)).thenReturn(cash);

    assertThatThrownBy(
            () -> evidenceDocumentService.create(7L, 11L, officialRequest("본문")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
  }

  @Test
  void searchRanksTitleMatchAndReturnsOnlyASnippet() {
    EvidenceDocument titleMatch =
        document("애플 실적 발표", "분기 실적과 매출 전망", Instant.parse("2026-09-01T00:00:00Z"));
    EvidenceDocument contentMatch =
        document("시장 동향", "시장 참가자가 애플 실적을 언급했다.", Instant.parse("2026-09-02T00:00:00Z"));
    when(evidenceDocumentRepository
            .findAllByUserIdAndAssetIdOrderByPublishedAtDescCreatedAtDesc(7L, 11L))
        .thenReturn(List.of(contentMatch, titleMatch));

    List<EvidenceSearchResult> results = evidenceDocumentService.search(7L, 11L, "애플 실적");

    assertThat(results).hasSize(2);
    assertThat(results.get(0).title()).isEqualTo("애플 실적 발표");
    assertThat(results.get(0).relevanceScore())
        .isGreaterThan(results.get(1).relevanceScore());
    assertThat(results.get(0).untrustedContent()).isTrue();
  }

  @Test
  void documentLookupIncludesUserAndAssetInRepositoryCondition() {
    EvidenceDocument document =
        document("애플 실적 발표", "본문", Instant.parse("2026-09-01T00:00:00Z"));
    when(evidenceDocumentRepository.findByIdAndUserIdAndAssetId(31L, 7L, 11L))
        .thenReturn(Optional.of(document));

    evidenceDocumentService.getDocument(7L, 11L, 31L);

    verify(evidenceDocumentRepository).findByIdAndUserIdAndAssetId(31L, 7L, 11L);
  }

  private EvidenceDocumentCreateRequest officialRequest(String content) {
    return new EvidenceDocumentCreateRequest(
        EvidenceSourceType.OFFICIAL,
        "애플 공식 발표",
        "Apple",
        "https://www.apple.com/newsroom/example",
        Instant.parse("2026-09-01T00:00:00Z"),
        content);
  }

  private EvidenceDocument document(String title, String content, Instant publishedAt) {
    return EvidenceDocument.create(
        7L,
        11L,
        "AAPL",
        EvidenceSourceType.NEWS,
        EvidenceTrust.USER_ASSERTED_NEWS,
        title,
        "Example News",
        "https://example.com/article",
        publishedAt,
        content,
        "hash");
  }
}
