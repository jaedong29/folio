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

  /** 항상 KRW 기준 평균 매입 단가 (매수 시점 환율로 환산된 값). */
  @Column(name = "avg_price", precision = 20, scale = 8)
  private BigDecimal avgPrice;

  /** 원래 통화 기준 현재가. CASH/BANK 는 항상 1. */
  @Column(name = "current_price", precision = 20, scale = 8)
  private BigDecimal currentPrice;

  @Column(name = "price_updated_at")
  private LocalDateTime priceUpdatedAt;

  @Column(nullable = false, length = 10)
  private String currency;

  /** 현재 환율(원/통화). 국내주식·원화현금은 1. */
  @Column(name = "exchange_rate", nullable = false, precision = 10, scale = 4)
  private BigDecimal exchangeRate;

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
    this.realizedPnl = BigDecimal.ZERO;
    this.exchangeRate = BigDecimal.ONE;
    this.source = AssetSource.MANUAL;
    if (type.isCashLike()) {
      // 현금성 자산은 "보유 금액이 곧 수량"이므로 단가를 1로 고정해 평가금액 공식을 공유한다.
      this.avgPrice = BigDecimal.ONE;
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
    BigDecimal previousCost = this.quantity.multiply(avgPriceOrZero());
    BigDecimal addedCost = quantity.multiply(buyPriceKrw);
    BigDecimal newQuantity = this.quantity.add(quantity);

    this.avgPrice =
        previousCost.add(addedCost).divide(newQuantity, CALC_SCALE, RoundingMode.HALF_UP);
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
      throw new InsufficientAssetQuantityException(this.quantity, quantity);
    }

    BigDecimal sellPriceKrw = price.multiply(exchangeRate);
    BigDecimal profitPerUnit = sellPriceKrw.subtract(avgPriceOrZero());
    this.realizedPnl =
        this.realizedPnl
            .add(profitPerUnit.multiply(quantity))
            .setScale(CALC_SCALE, RoundingMode.HALF_UP);
    this.quantity = this.quantity.subtract(quantity);
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
      throw new InsufficientAssetQuantityException(this.quantity, amount);
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
    requirePositive(price, "현재가");
    this.currentPrice = price;
    this.priceUpdatedAt = LocalDateTime.now();
    this.source = source;
  }

  /**
   * 현재 환율을 갱신한다. 거래가 아니므로 Transaction 을 만들지 않는다.
   *
   * @param rate 원/통화 환율
   * @throws BusinessException 환율이 0 이하인 경우 {@code INVALID_INPUT}
   */
  public void updateExchangeRate(BigDecimal rate) {
    requirePositive(rate, "환율");
    this.exchangeRate = rate;
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
    if (currentPrice == null) {
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
   * @return {@code quantity × avgPrice}. 평단가가 없으면 0
   */
  public BigDecimal getCost() {
    return quantity.multiply(avgPriceOrZero()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * 평가손익(KRW)을 계산한다.
   *
   * @return {@code 평가금액 - 매입금액}. 평가금액을 계산할 수 없으면 {@code null}
   */
  public BigDecimal getUnrealizedPnl() {
    BigDecimal valuation = getValuation();
    if (valuation == null) {
      return null;
    }
    return valuation.subtract(getCost());
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
    if (pnl == null || cost.compareTo(BigDecimal.ZERO) == 0) {
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

  // ---------------------------------------------------------------------
  // 내부 검증
  // ---------------------------------------------------------------------

  private BigDecimal avgPriceOrZero() {
    return avgPrice == null ? BigDecimal.ZERO : avgPrice;
  }

  private void requireInvestmentType(String action) {
    if (!type.isInvestment()) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "%s는 STOCK/CRYPTO 자산에만 가능합니다. (현재 타입: %s)".formatted(action, type));
    }
  }

  private void requireCashLikeType(String action) {
    if (!type.isCashLike()) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "%s는 CASH/BANK 자산에만 가능합니다. (현재 타입: %s)".formatted(action, type));
    }
  }

  private void requirePositive(BigDecimal value, String fieldName) {
    if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "%s은(는) 0보다 커야 합니다.".formatted(fieldName));
    }
  }
}
