package com.assetdashboard.domain.asset.service;

import com.assetdashboard.domain.asset.dto.AssetCreateRequest;
import com.assetdashboard.domain.asset.dto.AssetCreationResult;
import com.assetdashboard.domain.asset.dto.AssetResponse;
import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.infra.price.PriceQuote;
import com.assetdashboard.infra.price.fx.FxRateQuote;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검증을 마친 자산 등록을 실제로 저장하는 <b>트랜잭션 경계</b>.
 *
 * <p>{@link AssetService#create} 는 심볼 검증을 위해 외부 HTTP 를 호출하므로 트랜잭션 밖에서 돌아야 한다. 같은
 * 클래스 안의 {@code @Transactional} 메서드를 자기 자신이 호출하면 프록시를 타지 않아 트랜잭션이 걸리지 않으므로,
 * 저장 부분만 별도 빈으로 분리했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetRegistrar {

  private final AssetRepository assetRepository;
  private final TransactionRepository transactionRepository;

  /**
   * 자산을 새로 저장하거나, 삭제되었던 동일 심볼 자산을 되살린다.
   *
   * @param userId 인증된 사용자 id
   * @param request 등록 요청
   * @param symbol 정규화된 심볼
   * @param initialQuote 등록 시점에 확보한 시세. 없으면 {@code null}
   * @param initialRate 등록 시점에 확보한 현재 환율. 없으면 {@code null}
   * @return 등록 결과 (신규 생성인지 복구인지 포함)
   */
  @Transactional
  public AssetCreationResult persist(
      Long userId,
      AssetCreateRequest request,
      String symbol,
      PriceQuote initialQuote,
      FxRateQuote initialRate) {

    Optional<Asset> existing =
        assetRepository.findByUserIdAndTypeAndSymbol(userId, request.type(), symbol);
    boolean restored = existing.isPresent();

    Asset asset;
    if (existing.isPresent()) {
      asset = existing.get();
      boolean openingPositionEntered =
          request.initialQuantity().compareTo(BigDecimal.ZERO) > 0
              || request.averagePrice() != null;
      boolean hasTransactions = transactionRepository.existsByAssetId(asset.getId());

      if (hasTransactions && openingPositionEntered) {
        throw new BusinessException(
            ErrorCode.INVALID_INPUT,
            "삭제 전 거래 내역이 있는 자산입니다. 수량·평단을 비우고 등록하면 기존 Position과 거래 내역을 함께 복구합니다.");
      }

      asset.restore(request.resolvedName(), request.normalizedCurrency());
      if (!hasTransactions) {
        // 거래 이력이 없는 삭제 자산은 등록 폼에 방금 입력한 값을 새 시작 상태로 사용한다.
        // 이전 구현은 이 값을 조용히 무시해, 평단을 입력해도 계속 "평단 미입력"으로 보였다.
        initializeOpeningPosition(asset, request);
      }
      log.info(
          "[Asset] 삭제되었던 자산을 복구 assetId={} userId={} symbol={} openingPositionApplied={}",
          asset.getId(),
          userId,
          symbol,
          !hasTransactions);
    } else {
      asset =
          Asset.create(
              userId,
              request.type(),
              symbol,
              request.resolvedName(),
              request.normalizedCurrency());
      initializeOpeningPosition(asset, request);
      assetRepository.save(asset);
    }

    if (initialQuote != null) {
      // 등록 시점에 확보한 가격을 초기 현재가로 남긴다. 첫 대시보드 진입에서 0원으로 보이지 않게 하기 위함이다.
      asset.updateCurrentPrice(initialQuote.price(), AssetSource.API);
      if (initialQuote.exchangeRate() != null) {
        asset.updateExchangeRate(initialQuote.exchangeRate(), initialQuote.fetchedAt());
      }
    }
    if (initialRate != null) {
      // 현재 환율은 초기 평단 계산에 쓴 과거 환율과 별개다. 등록 시점의 시장 환율로 평가액을 만든다.
      asset.updateExchangeRate(initialRate.krwRate(), initialRate.fetchedAt());
    }
    return new AssetCreationResult(AssetResponse.from(asset), restored);
  }

  private void initializeOpeningPosition(Asset asset, AssetCreateRequest request) {
    BigDecimal openingRate =
        "KRW".equals(request.normalizedCurrency())
            ? BigDecimal.ONE
            : request.averageExchangeRate();
    asset.initializePosition(request.initialQuantity(), request.averagePrice(), openingRate);
  }
}
