package com.assetdashboard.evidence.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SymbolEvidenceServiceTest {

  @Mock private AssetService assetService;
  @Mock private EvidenceDocumentRepository evidenceDocumentRepository;
  private SymbolEvidenceService service;

  @BeforeEach
  void setUp() {
    service =
        new SymbolEvidenceService(
            assetService,
            evidenceDocumentRepository,
            Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC));
    when(assetService.getOwnedAsset(7L, 42L))
        .thenReturn(Asset.create(7L, AssetType.STOCK, "AAPL", "Apple", "USD"));
  }

  @Test
  void returnsUnavailableWithoutInventingDocumentsWhenNoneAreRegistered() {
    when(evidenceDocumentRepository.findAllByUserIdAndAssetIdOrderByPublishedAtDescCreatedAtDesc(
            7L, 42L))
        .thenReturn(List.of());

    SymbolEvidenceResponse response = service.getEvidence(7L, 42L);

    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.UNAVAILABLE);
    assertThat(response.items()).isEmpty();
    assertThat(response.documentCount()).isZero();
    verify(assetService).getOwnedAsset(7L, 42L);
  }

  @Test
  void confirmsOnlyWhenEveryDocumentIsVerifiedOfficial() {
    when(evidenceDocumentRepository.findAllByUserIdAndAssetIdOrderByPublishedAtDescCreatedAtDesc(
            7L, 42L))
        .thenReturn(List.of(document(EvidenceTrust.VERIFIED_OFFICIAL)));

    SymbolEvidenceResponse response = service.getEvidence(7L, 42L);

    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.CONFIRMED);
    assertThat(response.items()).singleElement().satisfies(
        item -> assertThat(item.trust()).isEqualTo(EvidenceTrust.VERIFIED_OFFICIAL));
  }

  @Test
  void treatsUserAssertedTrustAsPartialNotConfirmed() {
    when(evidenceDocumentRepository.findAllByUserIdAndAssetIdOrderByPublishedAtDescCreatedAtDesc(
            7L, 42L))
        .thenReturn(List.of(document(EvidenceTrust.USER_ASSERTED_OFFICIAL)));

    SymbolEvidenceResponse response = service.getEvidence(7L, 42L);

    assertThat(response.conclusion()).isEqualTo(EvidenceConclusion.PARTIAL);
  }

  private EvidenceDocument document(EvidenceTrust trust) {
    return EvidenceDocument.create(
        7L,
        42L,
        "AAPL",
        EvidenceSourceType.OFFICIAL,
        trust,
        "Apple 10-K",
        "SEC",
        "https://sec.gov/filing/1",
        Instant.parse("2026-09-01T00:00:00Z"),
        "Annual report content.",
        "hash-1");
  }
}
