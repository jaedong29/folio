package com.assetdashboard.evidence.calculation;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse.EvidenceWarning;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse.ExchangeRateEvidence;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse.PriceEvidence;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse.TransactionEvidence;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse.TransactionItem;
import com.assetdashboard.infra.price.PriceProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** LLM 호출 전에 신뢰 가능한 계산 근거를 구조화하는 읽기 전용 서비스. */
@Service
@RequiredArgsConstructor
public class AssetEvidenceService {

  private static final int RECENT_TRANSACTION_LIMIT = 20;

  private final AssetService assetService;
  private final TransactionRepository transactionRepository;
  private final PriceProperties priceProperties;

  /**
   * 내 자산의 계산값과 그 값에 사용된 가격·환율·최근 거래를 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 자산 id
   * @return LLM이 그대로 Tool 결과로 사용할 수 있는 구조화된 근거
   */
  @Transactional(readOnly = true)
  public AssetEvidenceResponse getAssetEvidence(Long userId, Long assetId) {
    Asset asset = assetService.getOwnedAsset(userId, assetId);
    List<EvidenceWarning> warnings = collectWarnings(asset);
    Page<Transaction> transactionPage =
        transactionRepository.findAllByAssetIdOrderByTradedAtDescIdDesc(
            assetId, PageRequest.of(0, RECENT_TRANSACTION_LIMIT));
    List<TransactionItem> transactions =
        transactionPage.getContent().stream().map(TransactionItem::from).toList();

    return new AssetEvidenceResponse(
        UUID.randomUUID().toString(),
        LocalDateTime.now(),
        conclusion(asset, warnings),
        AssetEvidenceResponse.AssetCalculation.from(asset),
        priceEvidence(asset),
        exchangeRateEvidence(asset),
        List.copyOf(warnings),
        new TransactionEvidence(
            transactionPage.getTotalElements(),
            transactions.size(),
            transactionPage.getTotalElements() > transactions.size(),
            transactions));
  }

  private List<EvidenceWarning> collectWarnings(Asset asset) {
    List<EvidenceWarning> warnings = new ArrayList<>();
    long ttlMinutes = priceProperties.cacheTtlMinutes();

    if (asset.getCurrentPrice() == null) {
      warnings.add(new EvidenceWarning("PRICE_MISSING", "현재가를 확인할 수 없습니다."));
    } else if (asset.isPriceStale(ttlMinutes)) {
      warnings.add(new EvidenceWarning("PRICE_STALE", "현재가가 갱신 기준 시간을 지났습니다."));
    }

    if (asset.isValuationBlockedByExchangeRate()) {
      warnings.add(new EvidenceWarning("FX_MISSING", "현재 원화 환율을 확인할 수 없습니다."));
    } else if (!"KRW".equalsIgnoreCase(asset.getCurrency())
        && asset.isExchangeRateStale(ttlMinutes)) {
      warnings.add(new EvidenceWarning("FX_STALE", "현재 환율이 갱신 기준 시간을 지났습니다."));
    }

    if (asset.getQuantity().compareTo(BigDecimal.ZERO) > 0 && asset.getAvgPrice() == null) {
      warnings.add(new EvidenceWarning("COST_BASIS_MISSING", "평균 매입 단가가 없어 평가손익을 계산할 수 없습니다."));
    }
    return warnings;
  }

  private EvidenceConclusion conclusion(Asset asset, List<EvidenceWarning> warnings) {
    if (asset.getValuation() == null) {
      return EvidenceConclusion.UNAVAILABLE;
    }
    return warnings.isEmpty() ? EvidenceConclusion.CONFIRMED : EvidenceConclusion.PARTIAL;
  }

  private PriceEvidence priceEvidence(Asset asset) {
    EvidenceValueStatus status;
    if (asset.getCurrentPrice() == null) {
      status = EvidenceValueStatus.MISSING;
    } else if (asset.isPriceStale(priceProperties.cacheTtlMinutes())) {
      status = EvidenceValueStatus.STALE;
    } else {
      status = EvidenceValueStatus.FRESH;
    }
    return new PriceEvidence(
        asset.getCurrentPrice(), asset.getSource(), asset.getPriceUpdatedAt(), status);
  }

  private ExchangeRateEvidence exchangeRateEvidence(Asset asset) {
    if ("KRW".equalsIgnoreCase(asset.getCurrency())) {
      return new ExchangeRateEvidence(
          BigDecimal.ONE, asset.getExchangeRateUpdatedAt(), EvidenceValueStatus.FIXED);
    }
    if (asset.isValuationBlockedByExchangeRate()) {
      return new ExchangeRateEvidence(
          null, asset.getExchangeRateUpdatedAt(), EvidenceValueStatus.MISSING);
    }
    EvidenceValueStatus status =
        asset.isExchangeRateStale(priceProperties.cacheTtlMinutes())
            ? EvidenceValueStatus.STALE
            : EvidenceValueStatus.FRESH;
    return new ExchangeRateEvidence(
        asset.getCurrentExchangeRate(), asset.getExchangeRateUpdatedAt(), status);
  }
}
