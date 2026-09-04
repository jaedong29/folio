package com.assetdashboard.evidence.evaluation;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 실제 사용자 금융정보를 외부 모델에 보내지 않고 평가할 수 있도록 local 합성 자산을 만든다. */
@Profile("local")
@Service
@RequiredArgsConstructor
public class FinancialAgentEvaluationFixtureService {

  private final AssetRepository assetRepository;

  @Transactional
  public EvaluationFixtureResponse create(Long userId, String caseId) {
    return switch (caseId) {
      case "fresh-valuation" -> createFreshValuation(userId);
      case "missing-price" -> createMissingPrice(userId);
      case "missing-fx" -> createMissingFx(userId);
      case "missing-cost-basis" -> createMissingCostBasis(userId);
      default ->
          throw new BusinessException(
              ErrorCode.INVALID_INPUT, "현재 생성 가능한 평가 fixture가 아닙니다: " + caseId);
    };
  }

  private EvaluationFixtureResponse createFreshValuation(Long userId) {
    Asset asset =
        Asset.create(
            userId,
            AssetType.STOCK,
            uniqueSymbol("FRESH"),
            "AAPL AI 평가용 정상 계산 자산",
            "USD");
    asset.initializePosition(
        new BigDecimal("2"), new BigDecimal("180"), new BigDecimal("1350"));
    asset.updateCurrentPrice(new BigDecimal("200"), AssetSource.MANUAL);
    asset.updateExchangeRate(new BigDecimal("1400"));
    return saved(
        asset,
        "fresh-valuation",
        "수량·현재가·현재 환율·평균 매입 단가가 모두 있는 합성 자산입니다.");
  }

  private EvaluationFixtureResponse createMissingPrice(Long userId) {
    Asset asset =
        Asset.create(
            userId,
            AssetType.STOCK,
            uniqueSymbol("NOPRICE"),
            "AI 평가용 가격 누락 자산",
            "KRW");
    asset.initializePosition(new BigDecimal("3"), new BigDecimal("50000"), BigDecimal.ONE);
    return saved(asset, "missing-price", "보유 수량과 평단가는 있지만 현재가가 없는 합성 자산입니다.");
  }

  private EvaluationFixtureResponse createMissingFx(Long userId) {
    Asset asset =
        Asset.create(userId, AssetType.STOCK, uniqueSymbol("NOFX"), "AI 평가용 환율 누락 자산", "USD");
    asset.initializePosition(
        BigDecimal.ONE, new BigDecimal("60000"), new BigDecimal("1400"));
    asset.updateCurrentPrice(new BigDecimal("65000"), AssetSource.MANUAL);
    return saved(asset, "missing-fx", "현재가는 있지만 현재 USD/KRW 환율은 없는 합성 자산입니다.");
  }

  private EvaluationFixtureResponse createMissingCostBasis(Long userId) {
    Asset asset =
        Asset.create(
            userId,
            AssetType.STOCK,
            uniqueSymbol("NOCOST"),
            "AI 평가용 원가 누락 자산",
            "KRW");
    asset.initializePosition(new BigDecimal("4"), null, null);
    asset.updateCurrentPrice(new BigDecimal("70000"), AssetSource.MANUAL);
    return saved(
        asset,
        "missing-cost-basis",
        "현재 평가금액은 계산되지만 평균 매입 단가가 없어 평가손익은 계산할 수 없는 합성 자산입니다.");
  }

  private EvaluationFixtureResponse saved(Asset asset, String caseId, String description) {
    Asset saved = assetRepository.save(asset);
    return new EvaluationFixtureResponse(saved.getId(), caseId, description);
  }

  private String uniqueSymbol(String prefix) {
    return "EVAL" + prefix + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
  }
}
