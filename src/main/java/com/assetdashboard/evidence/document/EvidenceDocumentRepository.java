package com.assetdashboard.evidence.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자·자산 조건을 모든 단건 조회에 포함하는 근거 문서 저장소. */
public interface EvidenceDocumentRepository extends JpaRepository<EvidenceDocument, Long> {

  boolean existsByUserIdAndAssetIdAndContentHash(Long userId, Long assetId, String contentHash);

  List<EvidenceDocument> findAllByUserIdAndAssetIdOrderByPublishedAtDescCreatedAtDesc(
      Long userId, Long assetId);

  Optional<EvidenceDocument> findByIdAndUserIdAndAssetId(
      Long id, Long userId, Long assetId);

  void deleteAllByUserId(Long userId);
}
