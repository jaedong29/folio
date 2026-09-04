package com.assetdashboard.domain.asset.entity;

import com.assetdashboard.domain.asset.exception.InsufficientAssetQuantityException;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.global.common.BaseTimeEntity;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 보유한 자산 하나. 이 프로젝트의 유일한 Aggregate Root.
 *
 * <p>수량·평단가·실현손익 같은 상태는 <b>모두 이 클래스의 도메인 메서드를 통해서만</b> 바뀐다. Service 계층은 상태를
 * 직접 대입하지 않으며, 계산식이 SQL 이나 Service 로 새어나가지 않게 한다(PRD 1장 성공 기준).
 *
 * <p>CASH/BANK 도 {@code currentPrice = 1} 로 두어 평가금액이
 * {@code quantity × currentPrice × exchangeRate} 한 공식으로 모든 타입에 적용된다. 덕분에 계산 로직 어디에도
 * {@code if (type == CASH)} 분기가 생기지 않는다.
 */
@Getter
@Entity
@Table(
    name = "assets",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_asset_user_type_symbol",
            columnNames = {"user_id", "type", "symbol"}),
    indexes = {@Index(name = "idx_asset_user_type", columnList = "user_id, type")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset extends BaseTimeEntity {

  /** 금액·평단가 계산에 사용하는 소수 자릿수. DB 컬럼(DECIMAL(20,8))과 맞춘다. */
  private static final int CALC_SCALE = 8;

  /** KRW 환산 금액을 응답할 때 사용하는 소수 자릿수. */
  private static final int MONEY_SCALE = 2;

  /** 수익률(%)의 소수 자릿수. */
  private static final int RATE_SCALE = 2;

  /** 매매 정산을 위해 모든 사용자에게 자동으로 제공하는 통화. */
  private static final Set<String> DEFAULT_SETTLEMENT_CURRENCIES =
      Set.of("KRW", "USD", "USDT");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 소유자 id. 객체 참조 대신 FK 값으로만 참조한다(PRD 3장). */
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AssetType type;

  /** 시세 조회에 사용하는 불변 키. 대문자로 정규화되어 저장된다. */
  @Column(nullable = false, length = 30)
  private String symbol;

  /** 화면 표시용 이름. 사용자가 자유롭게 바꿀 수 있으며 시세 조회에는 절대 쓰지 않는다. */
  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, precision = 20, scale = 8)
  private BigDecimal quantity;

  /**
   * 서비스를 처음 사용하기 시작했을 때 입력한 보유 수량.
   *
   * <p>이 값은 Transaction 이 아니다. 사용자가 과거 거래를 전부 복원하지 않아도 현재 Position 에서 시작할 수
   * 있게 하며, 거래 삭제 후 {@link #replay(List)} 할 때의 기준 상태로 사용한다.
   */
  @Column(name = "initial_quantity", precision = 20, scale = 8)
  private BigDecimal initialQuantity;

  /** 마지막 최초 보유 수량 정정 시각. 당일 손익에서 데이터 정정을 시장 손실로 오인하지 않게 한다. */
  @Column(name = "position_corrected_at")
  private LocalDateTime positionCorrectedAt;

  /** 항상 KRW 기준 평균 매입 단가 (매수 시점 환율로 환산된 값). */
  @Column(name = "avg_price", precision = 20, scale = 8)
  private BigDecimal avgPrice;

  /** 원래 통화 기준 평균 매입 단가. 화면 표시용이며, KRW 손익 계산은 {@link #avgPrice}를 사용한다. */
  @Column(name = "avg_price_original", precision = 20, scale = 8)
  private BigDecimal avgPriceOriginal;

  /** 최초 보유상태의 KRW 기준 평균 매입 단가. 거래 이력 재생의 기준값이다. */
  @Column(name = "initial_avg_price", precision = 20, scale = 8)
  private BigDecimal initialAvgPrice;

  /** 최초 보유상태의 원래 통화 기준 평균 매입 단가. */
  @Column(name = "initial_avg_price_original", precision = 20, scale = 8)
  private BigDecimal initialAvgPriceOriginal;

  /** 원래 통화 기준 현재가. CASH/BANK 는 항상 1. */
  @Column(name = "current_price", precision = 20, scale = 8)
  private BigDecimal currentPrice;

  @Column(name = "price_updated_at")
  private LocalDateTime priceUpdatedAt;

  @Column(nullable = false, length = 10)
  private String currency;

  /** 현재 환율(원/통화). KRW 자산은 1이며, 외화 자산은 현재 환율 입력 전까지 null 이다. */
  @Column(name = "exchange_rate", precision = 10, scale = 4)
  private BigDecimal exchangeRate;

  /** 현재 환율이 마지막으로 갱신된 시각. 가격 갱신 시각과 분리해 오래된 환율을 식별한다. */
  @Column(name = "exchange_rate_updated_at")
  private LocalDateTime exchangeRateUpdatedAt;

  /** 누적 실현손익(KRW). 매도 시에만 증감하며 전량 매도 후에도 초기화하지 않는다. */
  @Column(name = "realized_pnl", nullable = false, precision = 20, scale = 8)
  private BigDecimal realizedPnl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AssetSource source;

  /**
   * 낙관적 락 버전. 같은 Asset 에 동시에 들어온 두 번째 거래가 조용히 유실되지 않고
   * {@code OptimisticLockException} 으로 실패하게 만든다(PRD 6장).
   */
  @Version
  @Column(nullable = false)
  private Long version;

  /** Soft Delete 표시. NULL 이면 활성 자산이다. */
  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  private Asset(Long userId, AssetType type, String symbol, String name, String currency) {
    this.userId = userId;
    this.type = type;
    this.symbol = symbol;
    this.name = name;
    this.currency = currency;
    this.quantity = BigDecimal.ZERO;
    this.initialQuantity = BigDecimal.ZERO;
    this.realizedPnl = BigDecimal.ZERO;
    // 매수 당시 환율은 Transaction 에 저장한다. 외화 자산의 현재 환율은 별도로 입력·갱신해야 하므로
    // 등록 직후 1을 넣어 실제 환율처럼 계산하지 않는다. 알 수 없는 외화 환율은 null로 보존한다.
    this.exchangeRate = "KRW".equalsIgnoreCase(currency) ? BigDecimal.ONE : null;
    this.source = AssetSource.MANUAL;
    if (type.isCashLike()) {
      // 현금성 자산은 "보유 금액이 곧 수량"이므로 단가를 1로 고정해 평가금액 공식을 공유한다.
      this.avgPrice = BigDecimal.ONE;
      this.avgPriceOriginal = BigDecimal.ONE;
      this.initialAvgPrice = BigDecimal.ONE;
      this.initialAvgPriceOriginal = BigDecimal.ONE;
      this.currentPrice = BigDecimal.ONE;
    }
  }

  /**
   * 새 자산을 등록한다.
   *
   * @param userId 소유자 id
   * @param type 자산 종류
   * @param symbol 시세 조회 키 (대문자 정규화된 값이어야 한다)
   * @param name 화면 표시용 이름
   * @param currency currentPrice 의 통화 코드
   * @return 수량 0으로 초기화된 자산
   */
  public static Asset create(
      Long userId, AssetType type, String symbol, String name, String currency) {
    return new Asset(userId, type, symbol, name, currency);
  }

  /**
   * 서비스 사용 시작 시점의 현재 보유상태를 설정한다.
   *
   * <p>최초 보유량은 과거 매수 이벤트가 아니라 시작 상태다. 평균 매입 단가는 선택값이며, 입력되지 않으면 보유
   * 자산의 평가금액은 계산하되 평가손익과 수익률은 알 수 없는 값으로 남긴다.
   *
   * @param quantity 최초 보유 수량 또는 현금 잔액. null이면 0
   * @param averagePriceOriginal 원래 통화 기준 평균 매입 단가. 선택값
   * @param averageExchangeRate 평균 매입 단가를 KRW로 환산할 당시 환율. KRW 자산은 1
   * @throws BusinessException 수량이 음수이거나 평단가·환율 조합이 올바르지 않은 경우
   */
  public void initializePosition(
      BigDecimal quantity,
      BigDecimal averagePriceOriginal,
      BigDecimal averageExchangeRate) {
    BigDecimal initial = quantity == null ? BigDecimal.ZERO : quantity;
    requireNonNegative(initial, "초기 보유 수량");

    this.quantity = initial;
    this.initialQuantity = initial;

    if (type.isCashLike()) {
      this.avgPrice = BigDecimal.ONE;
      this.avgPriceOriginal = BigDecimal.ONE;
      this.initialAvgPrice = BigDecimal.ONE;
      this.initialAvgPriceOriginal = BigDecimal.ONE;
      return;
    }

    if (averagePriceOriginal == null) {
      this.avgPrice = null;
      this.avgPriceOriginal = null;
      this.initialAvgPrice = null;
      this.initialAvgPriceOriginal = null;
      return;
    }

    if (initial.compareTo(BigDecimal.ZERO) == 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "보유 수량이 0이면 평균 매입 단가를 입력할 수 없습니다.");
    }
    requirePositive(averagePriceOriginal, "평균 매입 단가");
    requirePositive(averageExchangeRate, "평균 매입 환율");

    BigDecimal averagePriceKrw =
        averagePriceOriginal
            .multiply(averageExchangeRate)
            .setScale(CALC_SCALE, RoundingMode.HALF_UP);
    this.avgPrice = averagePriceKrw;
    this.avgPriceOriginal = averagePriceOriginal.setScale(CALC_SCALE, RoundingMode.HALF_UP);
    this.initialAvgPrice = this.avgPrice;
    this.initialAvgPriceOriginal = this.avgPriceOriginal;
  }

  /**
   * 화면에 표시할 심볼을 반환한다.
   *
   * <p>저장된 {@code symbol}은 시세 제공자용 불변 키다. 국내주식의 경우 사용자가 입력한 종목코드만 보여주고,
   * Yahoo Finance용 {@code .KS}/{@code .KQ} 접미사는 화면에서 숨긴다.
   *
   * @return 사용자에게 보여줄 심볼
   */
  public String getDisplaySymbol() {
    if (type == AssetType.STOCK && symbol.matches("\\d{6}\\.(KS|KQ)")) {
      return symbol.substring(0, 6);
    }
    return symbol;
  }

  /**
   * 화면에 표시할 시장 또는 가격 출처를 반환한다.
   *
   * @return 사용자가 이해할 수 있는 시장명
   */
  public String getMarketLabel() {
    if (type == AssetType.CRYPTO) {
      return "Binance Spot";
    }
    if (type == AssetType.STOCK && symbol.endsWith(".KS")) {
      return "KOSPI";
    }
    if (type == AssetType.STOCK && symbol.endsWith(".KQ")) {
      return "KOSDAQ";
    }
    if (type == AssetType.STOCK) {
      return "해외주식";
    }
    return "투자 대기자금";
  }

  // ---------------------------------------------------------------------
  // 상태 변경 — 거래
  // ---------------------------------------------------------------------

  /**
   * 거래 이벤트를 종류에 따라 해당 도메인 메서드로 라우팅한다.
   *
   * <p>Service 가 {@code switch} 로 분기하지 않도록 라우팅 책임까지 Asset 이 갖는다. 새로운 거래 종류가 생겨도
   * 변경 지점이 이 메서드 하나로 모인다.
   *
   * @param tx 적용할 거래 이벤트
   * @throws InsufficientAssetQuantityException 매도·출금 수량이 보유 수량을 초과한 경우
   * @throws BusinessException 자산 종류에 맞지 않는 거래인 경우 {@code INVALID_INPUT}
   */
  public void applyTransaction(Transaction tx) {
    switch (tx.getType()) {
      case BUY -> buy(tx.getQuantity(), tx.getPrice(), tx.getExchangeRate());
      case SELL -> sell(tx.getQuantity(), tx.getPrice(), tx.getExchangeRate());
      case DEPOSIT -> deposit(tx.getQuantity());
      case WITHDRAW -> withdraw(tx.getQuantity());
    }
  }

  /**
   * 매수를 반영하고 평균 매입 단가를 재계산한다.
   *
   * <p>새 평단가(KRW) = (기존수량 × 기존평단가 + 신규수량 × 매수가 × 환율) / (기존수량 + 신규수량)
   *
   * <p>전량 매도 후 재매수처럼 기존 수량이 0인 경우 이 식은 자동으로 "새 평단가 = 이번 매수 단가"가 되므로 별도
   * 분기가 필요 없다.
   *
   * @param quantity 매수 수량
   * @param price 매수 단가 (원래 통화 기준)
   * @param exchangeRate 매수 시점 환율 (원/통화)
   * @throws BusinessException 투자 자산이 아닌 경우 {@code INVALID_INPUT}
   */
  public void buy(BigDecimal quantity, BigDecimal price, BigDecimal exchangeRate) {
    requireInvestmentType("매수");
    requirePositive(quantity, "수량");
    requirePositive(price, "가격");
    requirePositive(exchangeRate, "환율");

    BigDecimal buyPriceKrw = price.multiply(exchangeRate);
    BigDecimal previousQuantity = this.quantity;
    BigDecimal newQuantity = this.quantity.add(quantity);

    if (previousQuantity.compareTo(BigDecimal.ZERO) == 0) {
      this.avgPrice = buyPriceKrw.setScale(CALC_SCALE, RoundingMode.HALF_UP);
      this.avgPriceOriginal = price.setScale(CALC_SCALE, RoundingMode.HALF_UP);
    } else if (this.avgPrice != null) {
      BigDecimal previousCost = previousQuantity.multiply(this.avgPrice);
      BigDecimal addedCost = quantity.multiply(buyPriceKrw);
      this.avgPrice =
          previousCost.add(addedCost).divide(newQuantity, CALC_SCALE, RoundingMode.HALF_UP);

      // 기존 Position 의 원통화 평단을 알고 있을 때만 새 원통화 평단도 정확히 계산할 수 있다.
      if (this.avgPriceOriginal != null) {
        BigDecimal previousOriginalCost = previousQuantity.multiply(this.avgPriceOriginal);
        BigDecimal addedOriginalCost = quantity.multiply(price);
        this.avgPriceOriginal =
            previousOriginalCost
                .add(addedOriginalCost)
                .divide(newQuantity, CALC_SCALE, RoundingMode.HALF_UP);
      }
    }
    // 기존 Position 의 평단을 모르면 일부를 추가 매수해도 전체 원가는 여전히 알 수 없다.
    this.quantity = newQuantity;
  }

  /**
   * 매도를 반영하고 실현손익을 확정한다.
   *
   * <p>{@code realizedPnl += (매도가 × 매도시점환율 - avgPrice) × 매도수량}
   *
   * <p>평단가는 바꾸지 않는다. 남은 수량의 원가는 매도로 인해 변하지 않기 때문이다. 수량이 0이 되어도 평단가와
   * 실현손익을 초기화하지 않는다 — 초기화하면 전량 매도한 자산의 수익 기록이 시스템에서 사라진다.
   *
   * @param quantity 매도 수량
   * @param price 매도 단가 (원래 통화 기준)
   * @param exchangeRate 매도 시점 환율 (원/통화)
   * @throws InsufficientAssetQuantityException 보유 수량을 초과해 매도하려는 경우
   * @throws BusinessException 투자 자산이 아닌 경우 {@code INVALID_INPUT}
   */
  public void sell(BigDecimal quantity, BigDecimal price, BigDecimal exchangeRate) {
    requireInvestmentType("매도");
    requirePositive(quantity, "수량");
    requirePositive(price, "가격");
    requirePositive(exchangeRate, "환율");

    if (this.quantity.compareTo(quantity) < 0) {
      throw InsufficientAssetQuantityException.forPosition(
          this.name, this.symbol, this.quantity, quantity);
    }

    if (this.avgPrice != null) {
      BigDecimal sellPriceKrw = price.multiply(exchangeRate);
      BigDecimal profitPerUnit = sellPriceKrw.subtract(this.avgPrice);
      this.realizedPnl =
          this.realizedPnl
              .add(profitPerUnit.multiply(quantity))
              .setScale(CALC_SCALE, RoundingMode.HALF_UP);
    }
    this.quantity = this.quantity.subtract(quantity);
  }

  /**
   * 잘못 입력한 최초 보유 수량을 사용자가 확인한 현재 실제 수량에 맞게 정정한다.
   *
   * <p>현재 수량을 직접 덮어쓰지 않고 {@code 실제 수량 - 현재 수량}만큼 최초 보유 수량을 보정한 뒤 거래 이력을
   * 다시 재생한다. 따라서 이후 매수·매도가 있어도 평단가와 실현손익이 새 시작 수량을 기준으로 일관되게 계산된다.
   * 매도 Transaction이나 정산 자산 이동은 만들지 않는다.
   *
   * @param correctedQuantity 사용자가 확인한 현재 실제 보유 수량
   * @param transactions 거래 시점 오름차순으로 정렬된 기존 거래 이력
   * @throws BusinessException 현금성 자산이거나, 최초 수량만 고쳐서는 만들 수 없는 상태이거나, 정정 후 기존 매도
   *     시점의 보유 수량이 부족해지는 경우
   */
  public void correctCurrentQuantity(
      BigDecimal correctedQuantity, List<Transaction> transactions) {
    requireInvestmentType("보유 수량 정정");
    requireNonNegative(correctedQuantity, "실제 보유 수량");
    if (this.quantity.compareTo(correctedQuantity) == 0) {
      return;
    }

    BigDecimal previousInitialQuantity = this.initialQuantity;
    BigDecimal openingQuantity =
        previousInitialQuantity == null ? BigDecimal.ZERO : previousInitialQuantity;
    BigDecimal correctedOpeningQuantity =
        openingQuantity.add(correctedQuantity.subtract(this.quantity));
    if (correctedOpeningQuantity.compareTo(BigDecimal.ZERO) < 0) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          "최초 등록 수량만으로는 해당 수량으로 정정할 수 없습니다. 잘못 입력한 매수 거래가 있다면 거래 내역에서 삭제해주세요.");
    }

    BigDecimal previousQuantity = this.quantity;
    BigDecimal previousAvgPrice = this.avgPrice;
    BigDecimal previousAvgPriceOriginal = this.avgPriceOriginal;
    BigDecimal previousRealizedPnl = this.realizedPnl;
    this.initialQuantity = correctedOpeningQuantity;

    try {
      replay(transactions == null ? List.of() : transactions);
      this.positionCorrectedAt = LocalDateTime.now();
    } catch (InsufficientAssetQuantityException e) {
      restorePositionState(
          previousInitialQuantity,
          previousQuantity,
          previousAvgPrice,
          previousAvgPriceOriginal,
          previousRealizedPnl);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          "이 수량으로 정정하면 기존 매도 시점의 보유 수량이 부족해집니다. 거래 내역을 먼저 확인해주세요.");
    } catch (RuntimeException e) {
      restorePositionState(
          previousInitialQuantity,
          previousQuantity,
          previousAvgPrice,
          previousAvgPriceOriginal,
          previousRealizedPnl);
      throw e;
    }
  }

  private void restorePositionState(
      BigDecimal initialQuantity,
      BigDecimal quantity,
      BigDecimal avgPrice,
      BigDecimal avgPriceOriginal,
      BigDecimal realizedPnl) {
    this.initialQuantity = initialQuantity;
    this.quantity = quantity;
    this.avgPrice = avgPrice;
    this.avgPriceOriginal = avgPriceOriginal;
    this.realizedPnl = realizedPnl;
  }

  /**
   * 거래 이력 전체로부터 수량·평단가·실현손익을 처음부터 다시 계산한다.
   *
   * <p>거래를 삭제·수정했을 때 사용한다. 삭제된 거래의 영향만 "빼는" 방식은 매도 손익이 그 시점의 평단가에
   * 의존하기 때문에 성립하지 않는다 — 중간 거래 하나가 사라지면 그 이후 모든 계산의 전제가 바뀐다. 그래서
   * 되돌리는 대신 <b>남은 이력으로 다시 접는다(fold)</b>.
   *
   * <p>이 메서드의 존재가 곧 설계상의 답이다: <b>Asset 의 거래 상태는 Transaction 이력의 파생값이고,
   * 필드는 매번 재계산하지 않기 위한 스냅샷이다.</b> 평상시에는 증분 갱신으로 비용을 아끼고, 이력이 바뀐
   * 순간에만 전체 재계산을 한다.
   *
   * <p>시세·환율·이름처럼 거래에서 파생되지 않는 값은 건드리지 않는다.
   *
   * @param transactions 적용할 거래 이력. <b>호출자가 원하는 순서로 정렬해서 넘겨야 한다</b> (이 프로젝트는
   *     {@code tradedAt} 오름차순)
   * @throws InsufficientAssetQuantityException 재생 도중 보유 수량이 음수가 되는 경우
   */
  public void replay(List<Transaction> transactions) {
    this.quantity = initialQuantity == null ? BigDecimal.ZERO : initialQuantity;
    this.realizedPnl = BigDecimal.ZERO;
    this.avgPrice = type.isCashLike() ? BigDecimal.ONE : initialAvgPrice;
    this.avgPriceOriginal = type.isCashLike() ? BigDecimal.ONE : initialAvgPriceOriginal;

    for (Transaction tx : transactions) {
      applyTransaction(tx);
    }
  }

  /**
   * 현금성 자산에 입금을 반영한다.
   *
   * @param amount 입금액
   * @throws BusinessException 현금성 자산이 아닌 경우 {@code INVALID_INPUT}
   */
  public void deposit(BigDecimal amount) {
    requireCashLikeType("입금");
    requirePositive(amount, "금액");
    this.quantity = this.quantity.add(amount);
  }

  /**
   * 현금성 자산에서 출금을 반영한다.
   *
   * @param amount 출금액
   * @throws InsufficientAssetQuantityException 잔액이 부족한 경우
   * @throws BusinessException 현금성 자산이 아닌 경우 {@code INVALID_INPUT}
   */
  public void withdraw(BigDecimal amount) {
    requireCashLikeType("출금");
    requirePositive(amount, "금액");
    if (this.quantity.compareTo(amount) < 0) {
      throw InsufficientAssetQuantityException.forCashBalance(
          this.name, this.currency, this.quantity, amount);
    }
    this.quantity = this.quantity.subtract(amount);
  }

  // ---------------------------------------------------------------------
  // 상태 변경 — 시세·메타데이터
  // ---------------------------------------------------------------------

  /**
   * 현재가를 갱신한다. 거래가 아니므로 Transaction 을 만들지 않는다.
   *
   * <p>자동 조회 성공 시와 사용자 수동 입력 시 모두 이 메서드만을 통해 갱신되며, {@code priceUpdatedAt} 도 함께
   * 갱신되어 이후 TTL 판단의 기준이 된다.
   *
   * @param price 원래 통화 기준 현재가
   * @param source 이 값의 출처 (자동 조회면 {@code API}, 수동 입력이면 {@code MANUAL})
   * @throws BusinessException 가격이 0 이하인 경우 {@code INVALID_INPUT}
   */
  public void updateCurrentPrice(BigDecimal price, AssetSource source) {
    updateCurrentPrice(price, source, LocalDateTime.now());
  }

  /**
   * 조회 시각과 함께 현재가를 갱신한다.
   *
   * @param price 원래 통화 기준 현재가
   * @param source 이 값의 출처 (자동 조회면 {@code API}, 수동 입력이면 {@code MANUAL})
   * @param updatedAt 외부 제공자에서 값을 확보한 시각
   * @throws BusinessException 가격이 0 이하인 경우 {@code INVALID_INPUT}
   */
  public void updateCurrentPrice(BigDecimal price, AssetSource source, LocalDateTime updatedAt) {
    requirePositive(price, "현재가");
    this.currentPrice = price;
    this.priceUpdatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
    this.source = source;
  }

  /**
   * 현재 환율을 갱신한다. 거래가 아니므로 Transaction 을 만들지 않는다.
   *
   * @param rate 원/통화 환율
   * @throws BusinessException 환율이 0 이하인 경우 {@code INVALID_INPUT}
   */
  public void updateExchangeRate(BigDecimal rate) {
    updateExchangeRate(rate, LocalDateTime.now());
  }

  /**
   * 조회 시각과 함께 현재 환율을 갱신한다.
   *
   * @param rate 원/통화 환율
   * @param updatedAt 외부 제공자에서 값을 확보한 시각
   */
  public void updateExchangeRate(BigDecimal rate, LocalDateTime updatedAt) {
    requirePositive(rate, "환율");
    this.exchangeRate = rate;
    this.exchangeRateUpdatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
  }

  /**
   * 현재 보유분의 원화 평가가 환율 미확보 때문에 막혔는지 반환한다.
   *
   * <p>수량이 0이면 환율을 몰라도 평가금액은 정확히 0원이므로 화면에 경고하지 않는다.
   *
   * @return 보유 수량이 있는 외화 자산의 현재 환율이 없으면 true
   */
  public boolean isValuationBlockedByExchangeRate() {
    return quantity.compareTo(BigDecimal.ZERO) > 0
        && !"KRW".equalsIgnoreCase(currency)
        && exchangeRate == null;
  }

  /**
   * 화면·API에 노출할 현재 환율을 반환한다.
   *
   * @return 환율 미입력이면 null, 아니면 현재 환율
   */
  public BigDecimal getCurrentExchangeRate() {
    return isValuationBlockedByExchangeRate() ? null : exchangeRate;
  }

  /**
   * 표시용 이름과 통화를 수정한다. symbol 과 type 은 불변이므로 여기서 다루지 않는다.
   *
   * @param name 새 표시 이름 (null 이면 유지)
   * @param currency 새 통화 코드 (null 이면 유지)
   */
  public void updateDisplayInfo(String name, String currency) {
    if (name != null && !name.isBlank()) {
      this.name = name.trim();
    }
    if (currency != null && !currency.isBlank()) {
      this.currency = currency.trim().toUpperCase();
    }
  }

  /**
   * 자산을 Soft Delete 표시한다. 거래 내역과 실현손익은 그대로 보존된다.
   */
  public void softDelete() {
    this.deletedAt = LocalDateTime.now();
  }

  /**
   * Soft Delete 된 자산을 되살린다.
   *
   * <p>{@code (user_id, type, symbol)} 유니크 제약이 {@code deleted_at} 을 포함하지 않으므로, 삭제한 자산과
   * 같은 심볼을 다시 등록하면 새 row 를 만들 수 없다. 이때 기존 row 를 부활시켜 과거 거래 내역·평단가·실현손익이
   * 이어지게 한다.
   *
   * @param name 새 표시 이름
   * @param currency 새 통화 코드
   */
  public void restore(String name, String currency) {
    this.deletedAt = null;
    updateDisplayInfo(name, currency);
  }

  /**
   * Soft Delete 여부를 반환한다.
   *
   * @return 삭제 표시된 자산이면 {@code true}
   */
  public boolean isDeleted() {
    return deletedAt != null;
  }

  /**
   * 매매 정산용으로 자동 생성되는 기본 CASH 자산인지 반환한다.
   *
   * @return CASH이며 심볼이 KRW/USD/USDT이면 true
   */
  public boolean isDefaultSettlementAsset() {
    return type == AssetType.CASH && DEFAULT_SETTLEMENT_CURRENCIES.contains(symbol);
  }

  // ---------------------------------------------------------------------
  // 조회 — 계산된 값
  // ---------------------------------------------------------------------

  /**
   * 평가금액(KRW)을 계산한다.
   *
   * <p>{@code quantity × currentPrice × exchangeRate}. CASH/BANK 는 currentPrice 가 1로 고정되어
   * 있으므로 타입 분기 없이 같은 식이 적용된다.
   *
   * @return 평가금액. 현재가를 한 번도 확보하지 못한 자산이면 {@code null}
   */
  public BigDecimal getValuation() {
    // 0 USD/USDT는 환율을 아직 확보하지 못했더라도 수학적으로 정확히 0원이다.
    if (quantity.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
    if (currentPrice == null || exchangeRate == null || isValuationBlockedByExchangeRate()) {
      return null;
    }
    return quantity
        .multiply(currentPrice)
        .multiply(exchangeRate)
        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * 매입금액(KRW)을 계산한다.
   *
   * @return {@code quantity × avgPrice}. 보유 수량이 있지만 평단가를 모르면 {@code null}
   */
  public BigDecimal getCost() {
    if (quantity.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
    if (avgPrice == null) {
      return null;
    }
    return quantity.multiply(avgPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * 평가손익(KRW)을 계산한다.
   *
   * @return {@code 평가금액 - 매입금액}. 평가금액 또는 매입금액을 계산할 수 없으면 {@code null}
   */
  public BigDecimal getUnrealizedPnl() {
    BigDecimal valuation = getValuation();
    BigDecimal cost = getCost();
    if (valuation == null || cost == null) {
      return null;
    }
    return valuation.subtract(cost);
  }

  /**
   * 평가손익률(%)을 계산한다.
   *
   * @return {@code 평가손익 / 매입금액 × 100}. 매입금액이 0이거나 평가금액이 없으면 {@code null} (0으로 나누기
   *     방지 — PRD 4-5)
   */
  public BigDecimal getPnlRate() {
    BigDecimal cost = getCost();
    BigDecimal pnl = getUnrealizedPnl();
    if (pnl == null || cost == null || cost.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }
    return pnl.multiply(BigDecimal.valueOf(100)).divide(cost, RATE_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * 시세가 오래되었는지 판단한다.
   *
   * <p>자동 조회 대상이 아닌 CASH/BANK 는 항상 {@code false} 다. 투자 자산인데 갱신 시각이 없거나 TTL 을 넘겼다면
   * 화면에 "N분 전 기준"을 표시해야 하므로 {@code true} 를 돌려준다.
   *
   * @param ttlMinutes 시세 유효 시간(분)
   * @return 오래된 값을 쓰고 있으면 {@code true}
   */
  public boolean isPriceStale(long ttlMinutes) {
    if (type.isCashLike()) {
      return false;
    }
    if (priceUpdatedAt == null) {
      return true;
    }
    return priceUpdatedAt.isBefore(LocalDateTime.now().minusMinutes(ttlMinutes));
  }

  /**
   * 외화 자산의 현재 환율이 오래되었는지 판단한다.
   *
   * @param ttlMinutes 환율 유효 시간(분)
   * @return 환율이 없거나 TTL을 넘겼으면 true
   */
  public boolean isExchangeRateStale(long ttlMinutes) {
    if ("KRW".equalsIgnoreCase(currency)) {
      return false;
    }
    if (isValuationBlockedByExchangeRate() || exchangeRateUpdatedAt == null) {
      return true;
    }
    return exchangeRateUpdatedAt.isBefore(LocalDateTime.now().minusMinutes(ttlMinutes));
  }

  // ---------------------------------------------------------------------
  // 내부 검증
  // ---------------------------------------------------------------------

  private void requireInvestmentType(String action) {
    if (!type.isInvestment()) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          "STOCK/CRYPTO 자산에만 가능한 거래입니다. (요청: %s, 현재 타입: %s)".formatted(action, type));
    }
  }

  private void requireCashLikeType(String action) {
    if (!type.isCashLike()) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          "CASH/BANK 자산에만 가능한 거래입니다. (요청: %s, 현재 타입: %s)".formatted(action, type));
    }
  }

  private void requirePositive(BigDecimal value, String fieldName) {
    if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "%s은(는) 0보다 커야 합니다.".formatted(fieldName));
    }
  }

  private void requireNonNegative(BigDecimal value, String fieldName) {
    if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "%s은(는) 0 이상이어야 합니다.".formatted(fieldName));
    }
  }
}
