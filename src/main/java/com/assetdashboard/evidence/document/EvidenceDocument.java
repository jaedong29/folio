package com.assetdashboard.evidence.document;

import com.assetdashboard.global.common.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 등록된 투자 자산의 symbol에 사용자가 직접 연결한 기사·공식자료·메모. */
@Getter
@Entity
@Table(
    name = "evidence_documents",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_evidence_user_asset_hash",
            columnNames = {"user_id", "asset_id", "content_hash"}),
    indexes = {
      @Index(name = "idx_evidence_user_asset_published", columnList = "user_id, asset_id, published_at"),
      @Index(name = "idx_evidence_user_symbol", columnList = "user_id, symbol")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceDocument extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  /** 등록 시점 Asset의 불변 시세 조회 키. */
  @Column(nullable = false, length = 30)
  private String symbol;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 20)
  private EvidenceSourceType sourceType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private EvidenceTrust trust;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 120)
  private String publisher;

  @Column(name = "source_url", length = 2048)
  private String sourceUrl;

  /** 외부 자료가 발표된 시각. 알 수 없으면 null로 보존한다. */
  @Column(name = "published_at")
  private Instant publishedAt;

  /** 원문은 지시문이 포함될 수 있는 신뢰하지 않는 사용자 입력으로 취급한다. */
  @Lob
  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String content;

  @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
  private String contentHash;

  private EvidenceDocument(
      Long userId,
      Long assetId,
      String symbol,
      EvidenceSourceType sourceType,
      EvidenceTrust trust,
      String title,
      String publisher,
      String sourceUrl,
      Instant publishedAt,
      String content,
      String contentHash) {
    this.userId = userId;
    this.assetId = assetId;
    this.symbol = symbol;
    this.sourceType = sourceType;
    this.trust = trust;
    this.title = title;
    this.publisher = publisher;
    this.sourceUrl = sourceUrl;
    this.publishedAt = publishedAt;
    this.content = content;
    this.contentHash = contentHash;
  }

  public static EvidenceDocument create(
      Long userId,
      Long assetId,
      String symbol,
      EvidenceSourceType sourceType,
      EvidenceTrust trust,
      String title,
      String publisher,
      String sourceUrl,
      Instant publishedAt,
      String content,
      String contentHash) {
    return new EvidenceDocument(
        userId,
        assetId,
        symbol,
        sourceType,
        trust,
        title,
        publisher,
        sourceUrl,
        publishedAt,
        content,
        contentHash);
  }
}
