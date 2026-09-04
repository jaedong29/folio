package com.assetdashboard.domain.transaction.entity;

import com.assetdashboard.global.common.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Asset 에 발생한 변경 이벤트의 기록.
 *
 * <p>상태 변경 책임은 전적으로 Asset 에 있고 Transaction 은 "무슨 일이 있었는지"만 남긴다. 도메인 메서드를 두지
 * 않는 이유이며, 별도 Aggregate 가 아니므로 조회의 시작점이 되는 API({@code GET /api/transactions})도 만들지
 * 않는다(PRD 1장).
 *
 * <p>거래 시점의 {@code exchangeRate} 를 함께 박아두는 것이 중요하다. {@code assets.exchange_rate} 는
 * <b>현재</b> 환율이라 계속 덮어써지므로, 나중에 실현손익을 재계산·검증하려면 그 순간의 환율이 이벤트에 남아 있어야
 * 한다.
 */
@Getter
@Entity
@Table(
    name = "transactions",
    indexes = {@Index(name = "idx_tx_asset_traded_at", columnList = "asset_id, traded_at DESC")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 소속 Asset 의 id. 객체 참조를 쓰지 않아 Aggregate 경계를 넘는 그래프 탐색을 막는다(PRD 3장). */
  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransactionType type;

  @Column(nullable = false, precision = 20, scale = 8)
  private BigDecimal quantity;

  /** 거래 시점의 <b>원래 통화 기준</b> 단가. DEPOSIT/WITHDRAW 는 의미가 없어 null. */
  @Column(precision = 20, scale = 8)
  private BigDecimal price;

  /** 거래 시점의 환율(원/통화). BUY·SELL 모두 필수이며 국내주식·원화현금은 1이다. */
  @Column(name = "exchange_rate", precision = 10, scale = 4)
  private BigDecimal exchangeRate;

  /** 매수·매도 대금을 주고받은 투자 대기자금 Asset id. 연결하지 않은 기존 거래는 null이다. */
  @Column(name = "settlement_asset_id")
  private Long settlementAssetId;

  /** 정산 자산에서 증감한 원래 통화 금액({@code quantity × price}). */
  @Column(name = "settlement_amount", precision = 20, scale = 8)
  private BigDecimal settlementAmount;

  @Column(length = 255)
  private String memo;

  /** 사용자가 지정한 거래 시점. 미래 시각은 허용하지 않는다(PRD 4-8). */
  @Column(name = "traded_at", nullable = false)
  private LocalDateTime tradedAt;

  private Transaction(
      Long assetId,
      TransactionType type,
      BigDecimal quantity,
      BigDecimal price,
      BigDecimal exchangeRate,
      Long settlementAssetId,
      BigDecimal settlementAmount,
      String memo,
      LocalDateTime tradedAt) {
    this.assetId = assetId;
    this.type = type;
    this.quantity = quantity;
    this.price = price;
    this.exchangeRate = exchangeRate;
    this.settlementAssetId = settlementAssetId;
    this.settlementAmount = settlementAmount;
    this.memo = memo;
    this.tradedAt = tradedAt;
  }

  /**
   * 매수 이벤트를 생성한다.
   *
   * @param assetId 대상 Asset id
   * @param quantity 매수 수량
   * @param price 매수 단가 (원래 통화 기준)
   * @param exchangeRate 매수 시점 환율
   * @param settlementAssetId 매수대금을 차감할 투자 대기자금 id
   * @param settlementAmount 차감할 원래 통화 금액
   * @param memo 사용자 메모 (nullable)
   * @param tradedAt 거래 시점
   * @return 생성된 매수 이벤트
   */
  public static Transaction createBuy(
      Long assetId,
      BigDecimal quantity,
      BigDecimal price,
      BigDecimal exchangeRate,
      Long settlementAssetId,
      BigDecimal settlementAmount,
      String memo,
      LocalDateTime tradedAt) {
    return new Transaction(
        assetId,
        TransactionType.BUY,
        quantity,
        price,
        exchangeRate,
        settlementAssetId,
        settlementAmount,
        memo,
        tradedAt);
  }

  /**
   * 매도 이벤트를 생성한다.
   *
   * @param assetId 대상 Asset id
   * @param quantity 매도 수량
   * @param price 매도 단가 (원래 통화 기준)
   * @param exchangeRate 매도 시점 환율 (실현손익을 KRW 로 확정하는 데 사용)
   * @param settlementAssetId 매도대금을 입금할 투자 대기자금 id
   * @param settlementAmount 입금할 원래 통화 금액
   * @param memo 사용자 메모 (nullable)
   * @param tradedAt 거래 시점
   * @return 생성된 매도 이벤트
   */
  public static Transaction createSell(
      Long assetId,
      BigDecimal quantity,
      BigDecimal price,
      BigDecimal exchangeRate,
      Long settlementAssetId,
      BigDecimal settlementAmount,
      String memo,
      LocalDateTime tradedAt) {
    return new Transaction(
        assetId,
        TransactionType.SELL,
        quantity,
        price,
        exchangeRate,
        settlementAssetId,
        settlementAmount,
        memo,
        tradedAt);
  }

  /**
   * 입금 이벤트를 생성한다.
   *
   * @param assetId 대상 Asset id
   * @param quantity 입금액
   * @param memo 사용자 메모 (nullable)
   * @param tradedAt 거래 시점
   * @return 생성된 입금 이벤트
   */
  public static Transaction createDeposit(
      Long assetId,
      BigDecimal quantity,
      BigDecimal exchangeRate,
      String memo,
      LocalDateTime tradedAt) {
    return new Transaction(
        assetId,
        TransactionType.DEPOSIT,
        quantity,
        null,
        exchangeRate,
        null,
        null,
        memo,
        tradedAt);
  }

  /**
   * 출금 이벤트를 생성한다.
   *
   * @param assetId 대상 Asset id
   * @param quantity 출금액
   * @param memo 사용자 메모 (nullable)
   * @param tradedAt 거래 시점
   * @return 생성된 출금 이벤트
   */
  public static Transaction createWithdraw(
      Long assetId,
      BigDecimal quantity,
      BigDecimal exchangeRate,
      String memo,
      LocalDateTime tradedAt) {
    return new Transaction(
        assetId,
        TransactionType.WITHDRAW,
        quantity,
        null,
        exchangeRate,
        null,
        null,
        memo,
        tradedAt);
  }
}
