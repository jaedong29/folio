package com.assetdashboard.domain.asset.service;

import com.assetdashboard.domain.asset.dto.AssetCreateRequest;
import com.assetdashboard.domain.asset.dto.AssetCreationResult;
import com.assetdashboard.domain.asset.dto.AssetResponse;
import com.assetdashboard.domain.asset.dto.AssetUpdateRequest;
import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자산의 등록·조회·수정·삭제와 시세/환율 수동 갱신을 담당한다.
 *
 * <p>모든 단건 접근은 {@code findByIdAndUserIdAndDeletedAtIsNull} 을 통하므로, 소유권 검증을 빠뜨린 경로가
 * 존재할 수 없다(PRD 4-0 규칙 2). 상태 변경은 전부 Asset 의 도메인 메서드에 위임하고 이 클래스는 필드를 직접
 * 대입하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

  private final AssetRepository assetRepository;

  /**
   * 자산을 등록한다.
   *
   * <p>같은 {@code (type, symbol)} 자산이 이미 있으면 중복으로 거부하고, <b>삭제된 상태</b>라면 새로 만드는 대신
   * 기존 row 를 되살린다. {@code (user_id, type, symbol)} 유니크 제약이 {@code deleted_at} 을 포함하지 않아
   * 새 row 를 만들 수 없기도 하지만, 그보다 과거 거래 내역·평단가·실현손익을 이어받는 쪽이 Soft Delete 를 쓴 취지에
   * 맞다.
   *
   * @param userId 인증된 사용자 id
   * @param request 등록 요청
   * @return 등록 결과 (신규 생성인지 복구인지 포함)
   * @throws BusinessException 활성 상태의 동일 자산이 이미 있으면 {@code DUPLICATE_ASSET}
   */
  @Transactional
  public AssetCreationResult create(Long userId, AssetCreateRequest request) {
    String symbol = request.normalizedSymbol();
    Optional<Asset> existing =
        assetRepository.findByUserIdAndTypeAndSymbol(userId, request.type(), symbol);

    if (existing.isPresent()) {
      Asset asset = existing.get();
      if (!asset.isDeleted()) {
        throw new BusinessException(
            ErrorCode.DUPLICATE_ASSET,
            "이미 등록된 자산입니다. (%s / %s)".formatted(request.type(), symbol));
      }
      asset.restore(request.resolvedName(), request.normalizedCurrency());
      log.info("[Asset] 삭제되었던 자산을 복구 assetId={} userId={} symbol={}", asset.getId(), userId, symbol);
      return new AssetCreationResult(AssetResponse.from(asset), true);
    }

    Asset asset =
        Asset.create(
            userId,
            request.type(),
            symbol,
            request.resolvedName(),
            request.normalizedCurrency());
    return new AssetCreationResult(AssetResponse.from(assetRepository.save(asset)), false);
  }

  /**
   * 내 활성 자산 전체를 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @return 자산 목록 (종류, 이름 순)
   */
  public List<AssetResponse> getAssets(Long userId) {
    return assetRepository.findAllByUserIdAndDeletedAtIsNull(userId).stream()
        .sorted(Comparator.comparing(Asset::getType).thenComparing(Asset::getName))
        .map(AssetResponse::from)
        .toList();
  }

  /**
   * 내 자산 한 건을 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 자산 id
   * @return 자산 정보
   * @throws BusinessException 없거나 타인 소유이면 {@code ASSET_NOT_FOUND}
   */
  public AssetResponse getAsset(Long userId, Long assetId) {
    return AssetResponse.from(getOwnedAsset(userId, assetId));
  }

  /**
   * 표시 이름과 통화를 수정한다. symbol 과 type 은 불변이다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 자산 id
   * @param request 수정 요청
   * @return 수정된 자산 정보
   * @throws BusinessException 없거나 타인 소유이면 {@code ASSET_NOT_FOUND}
   */
  @Transactional
  public AssetResponse update(Long userId, Long assetId, AssetUpdateRequest request) {
    Asset asset = getOwnedAsset(userId, assetId);
    asset.updateDisplayInfo(request.name(), request.currency());
    return AssetResponse.from(asset);
  }

  /**
   * 자산을 Soft Delete 한다. 거래 내역과 실현손익은 보존된다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 자산 id
   * @throws BusinessException 없거나 타인 소유이면 {@code ASSET_NOT_FOUND}
   */
  @Transactional
  public void delete(Long userId, Long assetId) {
    getOwnedAsset(userId, assetId).softDelete();
  }

  /**
   * 현재가를 수동으로 갱신한다 (자동 조회 실패 시의 최후 폴백).
   *
   * @param userId 인증된 사용자 id
   * @param assetId 자산 id
   * @param currentPrice 원래 통화 기준 현재가
   * @return 갱신된 자산 정보
   * @throws BusinessException 없거나 타인 소유이면 {@code ASSET_NOT_FOUND}
   */
  @Transactional
  public AssetResponse updatePrice(Long userId, Long assetId, BigDecimal currentPrice) {
    Asset asset = getOwnedAsset(userId, assetId);
    // 수동 입력도 자동 조회와 동일하게 도메인 메서드를 거친다. priceUpdatedAt 이 함께 갱신되므로
    // 이후 TTL(15분) 동안은 자동 조회가 이 값을 덮어쓰지 않는다(PRD 4-2).
    asset.updateCurrentPrice(currentPrice, AssetSource.MANUAL);
    return AssetResponse.from(asset);
  }

  /**
   * 환율을 수동으로 갱신한다.
   *
   * @param userId 인증된 사용자 id
   * @param assetId 자산 id
   * @param exchangeRate 원/통화 환율
   * @return 갱신된 자산 정보
   * @throws BusinessException 없거나 타인 소유이면 {@code ASSET_NOT_FOUND}
   */
  @Transactional
  public AssetResponse updateExchangeRate(Long userId, Long assetId, BigDecimal exchangeRate) {
    Asset asset = getOwnedAsset(userId, assetId);
    asset.updateExchangeRate(exchangeRate);
    return AssetResponse.from(asset);
  }

  /**
   * 소유권을 조회 조건에 포함해 자산을 가져온다.
   *
   * <p>존재하지 않는 자산과 타인 소유 자산을 <b>동일하게</b> {@code ASSET_NOT_FOUND} 로 처리한다. 403 과 404 를
   * 구분해 응답하면 공격자가 id 를 순회하며 실재하는 자산 목록을 알아낼 수 있다(PRD 4-0 규칙 3).
   *
   * @param userId 인증된 사용자 id
   * @param assetId 자산 id
   * @return 내 소유의 활성 자산
   * @throws BusinessException 없거나 타인 소유이면 {@code ASSET_NOT_FOUND}
   */
  public Asset getOwnedAsset(Long userId, Long assetId) {
    return assetRepository
        .findByIdAndUserIdAndDeletedAtIsNull(assetId, userId)
        .orElseThrow(
            () -> {
              // 감사 추적을 위해 FORBIDDEN_ASSET_ACCESS 는 로그에만 남긴다.
              log.warn(
                  "[{}] 접근 거부 assetId={} userId={}",
                  ErrorCode.FORBIDDEN_ASSET_ACCESS.name(),
                  assetId,
                  userId);
              return new BusinessException(ErrorCode.ASSET_NOT_FOUND);
            });
  }
}
