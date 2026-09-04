package com.assetdashboard.domain.asset.dto;

import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.entity.StockMarket;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 자산 등록 요청 (PRD 4-2).
 *
 * @param type 자산 종류
 * @param symbol 사용자가 입력한 티커 또는 종목코드. 저장 시 외부 제공자용 심볼로 정규화된다
 * @param market STOCK 자산의 시장. KOSPI/KOSDAQ은 Yahoo 접미사를 자동으로 붙이고, OVERSEAS는 티커를 그대로 쓴다
 * @param name 화면 표시용 이름. <b>선택 입력</b>이며 비우면 symbol 을 그대로 사용한다 (PRD 5장)
 * @param currency currentPrice 의 통화 코드
 * @param quantity 서비스 시작 시점의 현재 보유 수량 또는 현금 잔액. 생략하면 0
 * @param averagePrice 평균 매입 단가(원래 통화 기준). 모르면 생략할 수 있다
 * @param averageExchangeRate 평균 매입 당시 환율. 외화 자산의 averagePrice를 입력할 때만 필요하다
 */
public record AssetCreateRequest(
    @Schema(example = "CRYPTO") @NotNull(message = "자산 종류는 필수입니다.") AssetType type,
    @Schema(example = "BTC", description = "주식은 티커 또는 종목코드만 입력한다. 예: NVDA, 000660")
        @NotBlank(message = "심볼은 필수입니다.")
        @Size(max = 30, message = "심볼은 30자를 넘을 수 없습니다.")
        @Pattern(regexp = "^[A-Za-z0-9.]+$", message = "심볼은 영문·숫자·마침표만 사용할 수 있습니다.")
        String symbol,
    @Schema(example = "KOSPI", nullable = true, description = "STOCK만 사용. KOSPI, KOSDAQ, OVERSEAS")
        StockMarket market,
    @Schema(example = "비트코인", description = "비우면 심볼이 표시 이름이 된다")
        @Size(max = 100, message = "이름은 100자를 넘을 수 없습니다.")
        String name,
    @Schema(example = "USDT")
        @NotBlank(message = "통화는 필수입니다.")
        @Size(max = 10, message = "통화 코드는 10자를 넘을 수 없습니다.")
        String currency,
    @Schema(example = "14.7", description = "현재 보유 수량. 과거 BUY 거래를 만들지 않는다")
        @PositiveOrZero(message = "초기 보유 수량은 0 이상이어야 합니다.")
        BigDecimal quantity,
    @Schema(example = "370.40", nullable = true, description = "원래 통화 기준 평단. 모르면 생략")
        @Positive(message = "평균 매입 단가는 0보다 커야 합니다.")
        BigDecimal averagePrice,
    @Schema(example = "1380", nullable = true, description = "평단 계산에 사용할 매수 당시 원/통화 환율")
        @Positive(message = "평균 매입 환율은 0보다 커야 합니다.")
        BigDecimal averageExchangeRate) {

  /**
   * 저장에 사용할 정규화된 심볼을 반환한다.
   *
   * <p>{@code btc} 와 {@code BTC} 가 서로 다른 값으로 취급되면 {@code (user_id, type, symbol)} 유니크
   * 제약이 중복 등록을 막지 못한다(PRD 4-8).
   *
   * @return 대문자로 변환된 심볼
   */
  public String normalizedSymbol() {
    if (type.isCashLike()) {
      return normalizedCurrency();
    }
    String normalized = symbol.trim().toUpperCase();
    if (type != AssetType.STOCK || market == null || normalized.contains(".")) {
      return normalized;
    }
    if (normalized.matches("\\d{6}") && market != StockMarket.OVERSEAS) {
      return normalized + market.yahooSuffix();
    }
    return normalized;
  }

  /**
   * 저장에 사용할 표시 이름을 반환한다.
   *
   * @return 입력된 이름, 비어 있으면 정규화된 심볼
   */
  public String resolvedName() {
    return (name == null || name.isBlank()) ? symbol.trim().toUpperCase() : name.trim();
  }

  /**
   * 저장에 사용할 통화 코드를 반환한다.
   *
   * @return 대문자로 변환된 통화 코드
   */
  public String normalizedCurrency() {
    return currency.trim().toUpperCase();
  }

  /**
   * 최초 보유 수량을 반환한다.
   *
   * @return 입력값, 생략했으면 0
   */
  public BigDecimal initialQuantity() {
    return quantity == null ? BigDecimal.ZERO : quantity;
  }
}
