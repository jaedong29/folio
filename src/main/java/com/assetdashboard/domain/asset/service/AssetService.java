package com.assetdashboard.domain.asset.service;

import com.assetdashboard.domain.asset.dto.AllocationResponse;
import com.assetdashboard.domain.asset.dto.AssetCreateRequest;
import com.assetdashboard.domain.asset.dto.AssetCreationResult;
import com.assetdashboard.domain.asset.dto.AssetResponse;
import com.assetdashboard.domain.asset.dto.AssetUpdateRequest;
import com.assetdashboard.domain.asset.dto.PortfolioResponse;
import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.infra.price.PriceProperties;
import com.assetdashboard.infra.price.PriceProviderException;
import com.assetdashboard.infra.price.PriceQueryService;
import com.assetdashboard.infra.price.PriceQuote;
import com.assetdashboard.infra.price.SymbolKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
 *
 * <p><b>클래스 레벨에 {@code @Transactional} 을 걸지 않았다.</b> 자산 등록과 Portfolio 조회는 도중에 외부
 * HTTP 호출을 하므로, 트랜잭션이 열린 채로 네트워크 I/O 를 기다리면 커넥션 풀이 고갈된다. 어떤 메서드가 트랜잭션
 * 안에서 도는지를 눈으로 확인할 수 있도록 메서드마다 명시한다(PRD 2장 제약 1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

  /** Portfolio 에 노출되는 자산 종류. */
  private static final List<AssetType> INVESTMENT_TYPES = List.of(AssetType.STOCK, AssetType.CRYPTO);

  /** 배분 비율(%)의 소수 자릿수. */
  private static final int RATIO_SCALE = 2;

  private final AssetRepository assetRepository;
  private final PriceProperties priceProperties;
  private final PriceQueryService priceQueryService;
  private final PriceRefreshService priceRefreshService;
  private final AssetRegistrar assetRegistrar;

  /**
   * 자산을 등록한다.
   *
   * <p>STOCK/CRYPTO 는 저장 전에 외부 API 로 심볼을 1회 검증하고, 성공하면 그 가격을 초기 현재가로 함께 저장한다.
   * 검증이 없으면 사용자가 {@code BITCOIN} 같은 잘못된 심볼을 넣어도 그대로 저장되고, 이후 시세 조회는 "실패해도
   * 예외를 던지지 않는" 정책 때문에 <b>조용히 계속 실패</b>한다 — 대시보드에 0원으로 표시되는 이유를 사용자가 영원히
   * 알 수 없게 된다. API 호출 1번으로 막을 수 있는 침묵형 버그다(PRD 4-2).
   *
   * <p>같은 {@code (type, symbol)} 자산이 이미 있으면 중복으로 거부하고, <b>삭제된 상태</b>라면 새로 만드는 대신
   * 기존 row 를 되살린다. {@code (user_id, type, symbol)} 유니크 제약이 {@code deleted_at} 을 포함하지 않아
   * 새 row 를 만들 수 없기도 하지만, 그보다 과거 거래 내역·평단가·실현손익을 이어받는 쪽이 Soft Delete 를 쓴 취지에
   * 맞다.
   *
   * @param userId 인증된 사용자 id
   * @param request 등록 요청
   * @return 등록 결과 (신규 생성인지 복구인지 포함)
   * @throws BusinessException 활성 상태의 동일 자산이 있으면 {@code DUPLICATE_ASSET}, 외부 API 에서 조회되지
   *     않는 심볼이면 {@code INVALID_SYMBOL}
   */
  public AssetCreationResult create(Long userId, AssetCreateRequest request) {
    String symbol = request.normalizedSymbol();

    // 1) 중복 확인을 먼저 한다. 어차피 거부할 요청에 외부 API 호출을 낭비하지 않기 위해서다.
    Optional<Asset> existing =
        assetRepository.findByUserIdAndTypeAndSymbol(userId, request.type(), symbol);
    if (existing.isPresent() && !existing.get().isDeleted()) {
      throw new BusinessException(
          ErrorCode.DUPLICATE_ASSET, "이미 등록된 자산입니다. (%s / %s)".formatted(request.type(), symbol));
    }

    // 2) 심볼 검증 — 트랜잭션 밖에서 수행한다.
    PriceQuote initialQuote = validateSymbolAndFetchQuote(request.type(), symbol);

    // 3) 저장 — 여기서부터만 트랜잭션이다. 별도 빈을 거쳐야 프록시가 적용된다.
    return assetRegistrar.persist(userId, request, symbol, initialQuote);
  }

  /**
   * 등록 시점에 심볼이 실제로 조회되는지 확인한다.
   *
   * <p>외부 API 가 <b>응답하지 않는 경우</b>(타임아웃·네트워크 장애)에는 검증을 통과시킨다. 우리 쪽 의존성 문제로
   * 사용자가 자산을 등록조차 못 하게 되는 것이 더 나쁜 실패이기 때문이다(PRD 4-2).
   *
   * @param type 자산 종류
   * @param symbol 정규화된 심볼
   * @return 조회에 성공했으면 시세, 자동 조회 대상이 아니거나 외부 장애면 null
   * @throws BusinessException 외부 API 에 존재하지 않는 심볼이면 {@code INVALID_SYMBOL}
   */
  private PriceQuote validateSymbolAndFetchQuote(AssetType type, String symbol) {
    if (!type.isInvestment()) {
      return null;
    }
    try {
      return priceQueryService.fetchDirect(new SymbolKey(type, symbol));
    } catch (PriceProviderException e) {
      if (e.isSymbolNotFound()) {
        throw new BusinessException(ErrorCode.INVALID_SYMBOL);
      }
      log.warn("[Asset] 외부 시세 API 장애로 심볼 검증을 건너뜀 symbol={} 사유={}", symbol, e.getMessage());
      return null;
    }
  }

  /**
   * 내 활성 자산 전체를 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @return 자산 목록 (종류, 이름 순)
   */
  @Transactional(readOnly = true)
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
  @Transactional(readOnly = true)
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

  // ---------------------------------------------------------------------
  // 조회 View — Portfolio / Allocation
  // ---------------------------------------------------------------------

  /**
   * Portfolio 화면용 투자 자산 목록을 조회한다 (PRD 4-5).
   *
   * <p>{@code type} 을 생략하면 STOCK 과 CRYPTO 를 모두 반환한다. 화면의 탭이 [전체][CRYPTO][STOCK] 이므로
   * 여기서 "전체"는 <b>투자 자산 전체</b>를 뜻하며 현금성 자산은 포함하지 않는다.
   *
   * @param userId 인증된 사용자 id
   * @param type 필터링할 자산 종류 (null 이면 STOCK + CRYPTO)
   * @param sort {@code 필드,방향} 형식의 정렬 조건 (기본값 {@code unrealizedPnl,desc})
   * @return 정렬된 Portfolio 목록
   * @throws BusinessException 투자 자산이 아닌 종류를 지정하면 {@code INVALID_INPUT}
   */
  public List<PortfolioResponse> getPortfolio(Long userId, AssetType type, String sort) {
    if (type != null && !type.isInvestment()) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "Portfolio 는 STOCK/CRYPTO 만 조회할 수 있습니다. (요청: %s)".formatted(type));
    }
    List<AssetType> types = (type != null) ? List.of(type) : INVESTMENT_TYPES;

    // 1) [TX] 자산 목록 조회 → 여기서 트랜잭션이 끝난다.
    List<Asset> assets = assetRepository.findAllByUserIdAndTypeInAndDeletedAtIsNull(userId, types);

    // 2) [TX 밖] 시세 갱신 — 외부 HTTP 호출은 반드시 트랜잭션 밖에서.
    List<Asset> refreshed = refreshPrices(assets);

    List<PortfolioResponse> items =
        refreshed.stream()
            .map(asset -> PortfolioResponse.from(asset, priceProperties.cacheTtlMinutes()))
            .collect(Collectors.toCollection(ArrayList::new));

    items.sort(PortfolioSort.parse(sort).comparator());
    return items;
  }

  /**
   * 자산들의 시세를 갱신한다. 외부 호출은 트랜잭션 밖에서, 반영만 짧은 별도 트랜잭션에서 수행된다.
   *
   * <p>흐름은 PRD 2장의 "조회 흐름"을 그대로 따른다.
   *
   * <ol>
   *   <li>갱신이 필요한 자산에서 {@code (type, symbol)} 집합을 뽑는다 — <b>중복 제거</b>. BTC 를 3개 자산에서
   *       쓰더라도 조회는 1회다.
   *   <li>{@code PriceQueryService} 가 캐시를 확인하고 미스/만료된 심볼만 외부에서 조회한다.
   *   <li>성공한 심볼만 별도 트랜잭션으로 {@code assets.current_price} 에 반영한다.
   *   <li>실패한 심볼은 아무 일도 일어나지 않는다 — 기존 저장값이 그대로 남아 폴백이 된다.
   * </ol>
   *
   * <p>시세 갱신 시각이 TTL 안에 있는 자산은 조회 대상에서 제외한다. 사용자가 수동으로 입력한 가격을 15분 안에
   * 자동 조회가 덮어쓰지 않게 하기 위함이다(PRD 4-2).
   *
   * @param assets 대상 자산 목록 (소유권이 이미 검증된 값)
   * @return 갱신이 반영된 자산 목록. 갱신할 것이 없으면 입력을 그대로 반환
   */
  private List<Asset> refreshPrices(List<Asset> assets) {
    long ttl = priceProperties.cacheTtlMinutes();
    List<Asset> targets =
        assets.stream()
            .filter(asset -> asset.getType().isInvestment())
            .filter(asset -> asset.isPriceStale(ttl))
            .toList();

    if (targets.isEmpty()) {
      return assets;
    }

    Set<SymbolKey> keys =
        targets.stream()
            .map(asset -> new SymbolKey(asset.getType(), asset.getSymbol()))
            .collect(Collectors.toSet());

    Map<SymbolKey, PriceQuote> quotes = priceQueryService.getPrices(keys);
    if (quotes.isEmpty()) {
      log.warn("[Price] 갱신 대상 {}건 중 확보한 시세 0건 — 저장된 값으로 폴백", targets.size());
      return assets;
    }

    List<Long> ids = assets.stream().map(Asset::getId).toList();
    return priceRefreshService.applyQuotes(ids, quotes);
  }

  /**
   * type 단위 자산 배분을 계산한다 (Pie Chart 용, PRD 4-4).
   *
   * <p>평가금액을 계산할 수 없는 자산(현재가를 한 번도 확보하지 못한 경우)은 0으로 취급한다. 비율의 분모는 전체
   * 평가금액 합계이며, 합계가 0이면 모든 비율을 0으로 반환한다.
   *
   * @param assets 집계 대상 자산 목록
   * @return 평가금액이 큰 순서의 배분 목록
   */
  public List<AllocationResponse> calculateAllocation(List<Asset> assets) {
    Map<AssetType, BigDecimal> byType = new EnumMap<>(AssetType.class);
    for (Asset asset : assets) {
      BigDecimal valuation = valuationOrZero(asset);
      byType.merge(asset.getType(), valuation, BigDecimal::add);
    }

    BigDecimal total =
        byType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

    return byType.entrySet().stream()
        .map(
            entry -> {
              BigDecimal ratio =
                  total.compareTo(BigDecimal.ZERO) == 0
                      ? BigDecimal.ZERO.setScale(RATIO_SCALE)
                      : entry
                          .getValue()
                          .multiply(BigDecimal.valueOf(100))
                          .divide(total, RATIO_SCALE, RoundingMode.HALF_UP);
              return new AllocationResponse(entry.getKey(), entry.getValue(), ratio);
            })
        .sorted(Comparator.comparing(AllocationResponse::valuationKRW).reversed())
        .toList();
  }

  /**
   * 자산의 평가금액을 반환하되, 계산할 수 없으면 0으로 대체한다.
   *
   * <p>총자산 합계에서 이런 자산을 제외하면 화면의 총액이 조용히 작아진다. 그 사실은 각 자산의
   * {@code valuationKRW: null} 과 {@code priceStale: true} 로 사용자에게 드러난다.
   *
   * @param asset 대상 자산
   * @return 평가금액. 계산 불가이면 0
   */
  public BigDecimal valuationOrZero(Asset asset) {
    BigDecimal valuation = asset.getValuation();
    return valuation == null ? BigDecimal.ZERO : valuation;
  }

  /**
   * 내 활성 자산 엔티티 전체를 시세 갱신까지 마쳐 반환한다. Dashboard Facade 가 조립에 사용한다.
   *
   * @param userId 인증된 사용자 id
   * @return 시세가 갱신된 활성 자산 목록
   */
  public List<Asset> getActiveAssetsWithFreshPrice(Long userId) {
    return refreshPrices(assetRepository.findAllByUserIdAndDeletedAtIsNull(userId));
  }

  /**
   * 삭제된 자산까지 포함한 누적 실현손익을 합산한다.
   *
   * <p>Soft Delete 를 쓰는 이유가 "확정된 수익 기록의 보존"인데, 모든 조회에서 삭제 자산을 빼면 정작 그 기록이
   * 대시보드에서 사라진다. 보유 목록·총자산·배분에서는 삭제 자산을 제외하되 <b>손익 합계에만</b> 포함한다.
   *
   * @param userId 인증된 사용자 id
   * @return 삭제 자산을 포함한 누적 실현손익 합계 (KRW)
   */
  @Transactional(readOnly = true)
  public BigDecimal getTotalRealizedPnl(Long userId) {
    return assetRepository.findAllByUserId(userId).stream()
        .map(Asset::getRealizedPnl)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
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
  @Transactional(readOnly = true)
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
