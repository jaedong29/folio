package com.assetdashboard.evidence.document;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.evidence.calculation.EvidenceConclusion;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Agent가 호출하는 읽기 전용 symbol 근거 조회. 항상 호출자 본인이 등록한 자료만 본다. */
@Service
@RequiredArgsConstructor
public class SymbolEvidenceService {

  private static final int AGENT_EVIDENCE_LIMIT = 5;

  private final AssetService assetService;
  private final EvidenceDocumentRepository evidenceDocumentRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public SymbolEvidenceResponse getEvidence(Long userId, Long assetId) {
    Asset asset = assetService.getOwnedAsset(userId, assetId);
    if (!asset.getType().isInvestment()) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "근거 자료는 STOCK/CRYPTO 자산에 대해서만 조회할 수 있습니다.");
    }

    List<SymbolEvidenceItem> items =
        evidenceDocumentRepository
            .findAllByUserIdAndAssetIdOrderByPublishedAtDescCreatedAtDesc(userId, assetId)
            .stream()
            .limit(AGENT_EVIDENCE_LIMIT)
            .map(SymbolEvidenceItem::from)
            .toList();
    boolean available = !items.isEmpty();
    boolean allVerified =
        available
            && items.stream().allMatch(item -> item.trust() == EvidenceTrust.VERIFIED_OFFICIAL);
    EvidenceConclusion conclusion =
        !available
            ? EvidenceConclusion.UNAVAILABLE
            : allVerified ? EvidenceConclusion.CONFIRMED : EvidenceConclusion.PARTIAL;

    return new SymbolEvidenceResponse(
        UUID.randomUUID().toString(),
        assetId,
        asset.getSymbol(),
        items.size(),
        conclusion,
        Instant.now(clock),
        items,
        available
            ? List.of("등록된 자료와 가격 변동의 인과관계는 확인할 수 없습니다.")
            : List.of("등록된 근거 자료가 없습니다."));
  }
}
