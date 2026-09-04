package com.assetdashboard.dashboard.facade;

import com.assetdashboard.dashboard.dto.DashboardResponse;
import com.assetdashboard.dashboard.dto.DashboardResponse.CashSummary;
import com.assetdashboard.dashboard.dto.DashboardResponse.DailyPnl;
import com.assetdashboard.dashboard.dto.DashboardResponse.InvestmentSummary;
import com.assetdashboard.dashboard.dto.DashboardResponse.MarketRates;
import com.assetdashboard.dashboard.dto.DashboardResponse.RecentTransaction;
import com.assetdashboard.dashboard.snapshot.DailyPnlResult;
import com.assetdashboard.dashboard.snapshot.DailyPnlService;
import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.infra.price.PriceProperties;
import com.assetdashboard.infra.price.fx.FxRateQueryService;
import com.assetdashboard.infra.price.fx.FxRateQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Dashboard 화면에 필요한 여러 조회 결과를 하나로 조립한다.
 *
 * <p>Dashboard 는 도메인이 아니라 <b>화면</b>이다. 자체 Entity 도 Repository 도 없고, 하는 일은 "이미 존재하는
 * 조회들을 모아 붙이는 것"뿐이다. 그래서 Service 가 아니라 Facade 로 두었다 — 여기에 비즈니스 규칙이 쌓이기
 * 시작하면 그건 Asset 도메인으로 옮겨야 한다는 신호다.
 */
@Component
@RequiredArgsConstructor
public class DashboardFacade {

  /** 화면에 노출하는 최근 거래 건수 (PRD 5장). */
  private static final int RECENT_TRANSACTION_LIMIT = 5;

  private static final int MONEY_SCALE = 2;
  private static final int RATE_SCALE = 2;

  private final AssetService assetService;
  private final TransactionRepository transactionRepository;
  private final PriceProperties priceProperties;
  private final FxRateQueryService fxRateQueryService;
  private final DailyPnlService dailyPnlService;

  /**
   * 대시보드 응답을 조립한다.
   *
   * <p>시세 갱신은 {@code AssetService} 안에서 트랜잭션 밖에 수행되며, 실패해도 예외가 올라오지 않고 마지막
   * 저장값으로 폴백된다. 따라서 외부 API 가 죽어도 이 화면은 항상 그려진다(PRD 8-1).
   *
   * @param userId 인증된 사용자 id
   * @return 대시보드 응답
   */
  public DashboardResponse getDashboard(Long userId) {
    return getDashboard(userId, false);
  }

  /**
   * Dashboard 응답을 조립한다.
   *
   * @param userId 인증된 사용자 id
   * @param force true 면 시세 TTL과 무관하게 외부 조회를 시도한다
   * @return Dashboard 응답
   */
  public DashboardResponse getDashboard(Long userId, boolean force) {
    List<Asset> assets = assetService.getActiveAssetsWithFreshPrice(userId, force);

    List<Asset> investments = assets.stream().filter(a -> a.getType().isInvestment()).toList();
    List<Asset> cashLike = assets.stream().filter(a -> a.getType().isCashLike()).toList();

    boolean investmentValuationComplete =
        investments.stream().allMatch(asset -> asset.getValuation() != null);
    BigDecimal investmentValuationSum = sumValuation(investments);
    BigDecimal investmentValuation =
        investmentValuationComplete ? investmentValuationSum : null;
    boolean costBasisMissing = investments.stream().anyMatch(asset -> asset.getCost() == null);
    BigDecimal investmentCost =
        costBasisMissing
            ? null
            : investments.stream().map(Asset::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    boolean investmentExchangeRateMissing =
        investments.stream().anyMatch(Asset::isValuationBlockedByExchangeRate);
    BigDecimal unrealizedPnl =
        !investmentValuationComplete || investmentExchangeRateMissing || costBasisMissing
            ? null
            : investmentValuationSum.subtract(investmentCost);
    boolean cashValuationComplete = cashLike.stream().allMatch(asset -> asset.getValuation() != null);
    BigDecimal cashValuationSum = sumValuation(cashLike);
    BigDecimal cashValuation = cashValuationComplete ? cashValuationSum : null;
    BigDecimal calculatedTotalAssetKrw =
        investmentValuationSum
            .add(cashValuationSum)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    boolean valuationComplete = assets.stream().allMatch(asset -> asset.getValuation() != null);
    BigDecimal totalAssetKrw = valuationComplete ? calculatedTotalAssetKrw : null;
    DailyPnlResult dailyPnl =
        dailyPnlService.calculate(userId, calculatedTotalAssetKrw, assets, valuationComplete);
    boolean exchangeRateMissing =
        assets.stream().anyMatch(Asset::isValuationBlockedByExchangeRate);
    MarketRates marketRates = findMarketRates(assets, force);
    boolean hasPortfolioContent =
        assets.stream()
            .anyMatch(
                asset ->
                    !asset.isDefaultSettlementAsset()
                        || asset.getQuantity().compareTo(BigDecimal.ZERO) > 0);

    InvestmentSummary investmentSummary =
        new InvestmentSummary(
            investmentValuation,
            investmentCost,
            unrealizedPnl,
            investmentExchangeRateMissing || costBasisMissing
                ? null
                : pnlRate(unrealizedPnl, investmentCost),
            // 삭제된 자산의 실현손익까지 합산한다. 그래야 "지금까지 얼마 벌었나"에 정확히 답할 수 있다.
            assetService.getTotalRealizedPnl(userId));

    return new DashboardResponse(
        totalAssetKrw,
        DailyPnl.from(dailyPnl),
        investmentSummary,
        new CashSummary(cashValuation),
        marketRates,
        assetService.calculateAllocation(assets),
        findRecentTransactions(assets),
        hasPortfolioContent,
        investments.stream().anyMatch(a -> a.isPriceStale(priceProperties.cacheTtlMinutes())),
        exchangeRateMissing);
  }

  /**
   * 현재 대시보드 자산 조회에서 확보한 시장 환율을 카드용으로 추출한다.
   *
   * <p>CRYPTO 시세는 {@code CryptoPriceProvider}가 Binance 가격과 Upbit KRW-USDT 가격을 함께 조회하므로,
   * 별도 환율 API를 한 번 더 호출하지 않고 해당 Asset의 최신 환율을 재사용한다.
   */
  private MarketRates findMarketRates(List<Asset> assets, boolean force) {
    FxRateQuote usd = findMarketRate("USD", assets, force).orElse(null);
    FxRateQuote usdt = findMarketRate("USDT", assets, force).orElse(null);
    return new MarketRates(
        usd == null ? null : usd.krwRate(),
        usd == null ? FxRateQueryService.USD_SOURCE : usd.source(),
        usd == null ? findStoredRateTime("USD", assets) : usd.fetchedAt(),
        usdt == null ? null : usdt.krwRate(),
        usdt == null ? FxRateQueryService.USDT_SOURCE : usdt.source(),
        usdt == null ? findStoredRateTime("USDT", assets) : usdt.fetchedAt());
  }

  private Optional<FxRateQuote> findMarketRate(
      String currency, List<Asset> assets, boolean force) {
    boolean alreadyRefreshed =
        assets.stream().anyMatch(asset -> currency.equalsIgnoreCase(asset.getCurrency()));
    Optional<FxRateQuote> quote =
        fxRateQueryService.fetchWithFallback(currency, force && !alreadyRefreshed);
    if (quote.isPresent()) {
      return quote;
    }
    return assets.stream()
        .filter(asset -> currency.equalsIgnoreCase(asset.getCurrency()))
        .filter(asset -> asset.getCurrentExchangeRate() != null)
        .findFirst()
        .map(
            asset ->
                new FxRateQuote(
                    currency,
                    asset.getCurrentExchangeRate(),
                    "마지막 저장값",
                    asset.getExchangeRateUpdatedAt() == null
                        ? asset.getPriceUpdatedAt()
                        : asset.getExchangeRateUpdatedAt()));
  }

  private java.time.LocalDateTime findStoredRateTime(String currency, List<Asset> assets) {
    return assets.stream()
        .filter(asset -> currency.equalsIgnoreCase(asset.getCurrency()))
        .map(Asset::getExchangeRateUpdatedAt)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * 내 자산들의 최근 거래를 모아 최신순으로 반환한다.
   *
   * <p>조회 대상 assetId 는 이미 {@code userId} 로 필터링된 목록에서만 나오므로, 다른 사용자의 거래가 섞일 수
   * 없다. {@code transactions} 테이블에 {@code user_id} 가 없어도 인가가 깨지지 않는 이유다.
   *
   * @param assets 내 활성 자산 목록
   * @return 최근 거래 (최대 5건)
   */
  private List<RecentTransaction> findRecentTransactions(List<Asset> assets) {
    if (assets.isEmpty()) {
      return List.of();
    }
    Map<Long, Asset> assetById =
        assets.stream().collect(java.util.stream.Collectors.toMap(Asset::getId, Function.identity()));

    List<Transaction> transactions =
        transactionRepository.findAllByAssetIdInOrderByTradedAtDescIdDesc(
            List.copyOf(assetById.keySet()), PageRequest.of(0, RECENT_TRANSACTION_LIMIT));

    return transactions.stream()
        .map(
            tx -> {
              Asset asset = assetById.get(tx.getAssetId());
              return new RecentTransaction(
                  tx.getId(),
                  tx.getAssetId(),
                  asset.getName(),
                  asset.getDisplaySymbol(),
                  tx.getType(),
                  tx.getQuantity(),
                  tx.getPrice(),
                  tx.getTradedAt());
            })
        .toList();
  }

  private BigDecimal sumValuation(List<Asset> assets) {
    return assets.stream()
        .map(assetService::valuationOrZero)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * 손익률을 계산한다. 매입금액이 0이면 0으로 나누기를 피하기 위해 null 을 반환한다.
   *
   * @param pnl 손익
   * @param cost 매입금액
   * @return 손익률(%). 매입금액이 0이면 null
   */
  private BigDecimal pnlRate(BigDecimal pnl, BigDecimal cost) {
    if (cost.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }
    return pnl.multiply(BigDecimal.valueOf(100)).divide(cost, RATE_SCALE, RoundingMode.HALF_UP);
  }
}
