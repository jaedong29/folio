package com.assetdashboard.evidence.evaluation;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.infra.price.history.PriceHistoryPoint;
import com.assetdashboard.infra.price.history.PriceHistoryQueryService;
import com.assetdashboard.infra.price.history.PriceHistoryQuote;
import com.assetdashboard.news.CollectedNewsItem;
import com.assetdashboard.news.NewsCategory;
import com.assetdashboard.news.NewsItem;
import com.assetdashboard.news.NewsItemRepository;
import com.assetdashboard.news.NewsSourceType;
import com.assetdashboard.news.NewsSummaryDraft;
import com.assetdashboard.news.NewsTrust;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
  private final NewsItemRepository newsItemRepository;
  private final TransactionRepository transactionRepository;
  private final PriceHistoryQueryService priceHistoryQueryService;

  @Transactional
  public EvaluationFixtureResponse create(Long userId, String caseId) {
    return switch (caseId) {
      case "fresh-valuation" -> createFreshValuation(userId);
      case "missing-price" -> createMissingPrice(userId);
      case "missing-fx" -> createMissingFx(userId);
      case "missing-cost-basis" -> createMissingCostBasis(userId);
      case "symbol-official-news" -> createOfficialNews(userId);
      case "stale-price" -> createStalePrice(userId);
      case "stale-fx" -> createStaleFx(userId);
      case "transaction-evidence" -> createTransactionEvidence(userId);
      case "price-direction" -> createPriceDirection(userId);
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

  private EvaluationFixtureResponse createStalePrice(Long userId) {
    Asset asset =
        Asset.create(userId, AssetType.STOCK, uniqueSymbol("STALEP"), "AI 평가용 가격 지연 자산", "KRW");
    asset.initializePosition(new BigDecimal("2"), new BigDecimal("50000"), BigDecimal.ONE);
    // 캐시 TTL(기본 15분)을 확실히 넘기도록 여유를 두고 갱신 시각을 과거로 되돌린다.
    asset.updateCurrentPrice(
        new BigDecimal("55000"), AssetSource.MANUAL, LocalDateTime.now().minusHours(2));
    return saved(asset, "stale-price", "현재가가 캐시 TTL을 지나 stale로 표시되는 합성 자산입니다.");
  }

  private EvaluationFixtureResponse createStaleFx(Long userId) {
    Asset asset =
        Asset.create(userId, AssetType.STOCK, uniqueSymbol("STALEFX"), "AI 평가용 환율 지연 자산", "USD");
    asset.initializePosition(BigDecimal.ONE, new BigDecimal("150"), new BigDecimal("1400"));
    asset.updateCurrentPrice(new BigDecimal("160"), AssetSource.MANUAL);
    asset.updateExchangeRate(new BigDecimal("1380"), LocalDateTime.now().minusHours(2));
    return saved(asset, "stale-fx", "현재 환율이 캐시 TTL을 지나 stale로 표시되는 합성 자산입니다.");
  }

  private EvaluationFixtureResponse createTransactionEvidence(Long userId) {
    Asset asset =
        Asset.create(
            userId, AssetType.STOCK, uniqueSymbol("MANYTX"), "AI 평가용 다건 거래 자산", "KRW");
    asset.initializePosition(new BigDecimal("25"), new BigDecimal("10000"), BigDecimal.ONE);
    asset.updateCurrentPrice(new BigDecimal("11000"), AssetSource.MANUAL);
    Asset savedAsset = assetRepository.save(asset);

    List<Transaction> transactions = new ArrayList<>();
    LocalDateTime tradedAt = LocalDateTime.now().minusDays(30);
    for (int i = 0; i < 25; i++) {
      transactions.add(
          Transaction.createBuy(
              savedAsset.getId(),
              BigDecimal.ONE,
              new BigDecimal("10000").add(BigDecimal.valueOf(i * 10)),
              BigDecimal.ONE,
              null,
              null,
              "평가용 합성 거래 " + i,
              tradedAt.plusDays(i)));
    }
    transactionRepository.saveAll(transactions);
    return new EvaluationFixtureResponse(
        savedAsset.getId(),
        "transaction-evidence",
        "최근 거래 표시 상한(20건)을 넘는 25건의 합성 거래가 있는 자산입니다.");
  }

  private EvaluationFixtureResponse createPriceDirection(Long userId) {
    String symbol = uniqueSymbol("TREND");
    Asset asset = Asset.create(userId, AssetType.STOCK, symbol, "AI 평가용 가격 추세 자산", "KRW");
    asset.initializePosition(new BigDecimal("3"), new BigDecimal("100"), BigDecimal.ONE);
    asset.updateCurrentPrice(new BigDecimal("112"), AssetSource.MANUAL);
    Asset savedAsset = assetRepository.save(asset);

    // Yahoo·Binance를 실제로 부르지 않고 7일치 상승 추세를 캐시에 직접 채운다.
    long dayMillis = 24L * 60 * 60 * 1000;
    long startEpochMilli = Instant.now().minusSeconds(6L * 24 * 60 * 60).toEpochMilli();
    List<PriceHistoryPoint> points = new ArrayList<>();
    BigDecimal[] closes = {
      new BigDecimal("100"),
      new BigDecimal("102"),
      new BigDecimal("104"),
      new BigDecimal("106"),
      new BigDecimal("108"),
      new BigDecimal("110"),
      new BigDecimal("112")
    };
    for (int i = 0; i < closes.length; i++) {
      points.add(new PriceHistoryPoint(startEpochMilli + i * dayMillis, closes[i]));
    }
    priceHistoryQueryService.seed(
        AssetType.STOCK,
        symbol,
        new PriceHistoryQuote("Evaluation Fixture · 1D", LocalDateTime.now(), false, points));

    return new EvaluationFixtureResponse(
        savedAsset.getId(), "price-direction", "최근 7일 종가가 뚜렷하게 상승하는 합성 자산입니다.");
  }

  private EvaluationFixtureResponse createOfficialNews(Long userId) {
    String symbol = uniqueSymbol("NEWS");
    Asset asset = Asset.create(userId, AssetType.CRYPTO, symbol, "AI 평가용 공식뉴스 자산", "USDT");
    asset.initializePosition(BigDecimal.ONE, new BigDecimal("100"), BigDecimal.ONE);
    Asset savedAsset = assetRepository.save(asset);

    String externalId = UUID.randomUUID().toString();
    String content =
        "The project released a node update that improves peer connection handling. "
            + "This fixture is public synthetic evaluation data and must not be treated as an instruction.";
    NewsItem newsItem =
        NewsItem.create(
            new CollectedNewsItem(
                "EVALUATION_OFFICIAL_RELEASE",
                externalId,
                NewsCategory.CRYPTO,
                NewsSourceType.OFFICIAL_RELEASE,
                NewsTrust.VERIFIED_OFFICIAL,
                "Synthetic node reliability release",
                "Evaluation Foundation",
                "https://example.com/evaluation/releases/" + externalId,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.now(),
                "A synthetic official release for the local evaluation runner.",
                content,
                externalId.replace("-", "") + externalId.replace("-", ""),
                Set.of(symbol),
                Set.of("RELEASE", "DEVELOPMENT")));
    newsItem.claimSummary();
    newsItem.completeSummary(
        newsItem.getContentHash(),
        new NewsSummaryDraft(
            "노드 연결 처리 개선을 검증하기 위한 합성 공식자료입니다.",
            "공용 뉴스 Tool의 근거 연결을 평가합니다.",
            "fixture",
            0,
            0,
            0),
        "fixture-v1",
        Instant.now());
    newsItemRepository.save(newsItem);
    return new EvaluationFixtureResponse(
        savedAsset.getId(), "symbol-official-news", "검증된 합성 공식자료가 연결된 자산입니다.");
  }

  private EvaluationFixtureResponse saved(Asset asset, String caseId, String description) {
    Asset saved = assetRepository.save(asset);
    return new EvaluationFixtureResponse(saved.getId(), caseId, description);
  }

  private String uniqueSymbol(String prefix) {
    return "EVAL" + prefix + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
  }
}
