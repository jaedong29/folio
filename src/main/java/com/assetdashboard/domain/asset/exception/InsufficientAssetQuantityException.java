package com.assetdashboard.domain.asset.exception;

import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.math.BigDecimal;

/**
 * 매도·출금 수량이 보유 수량을 초과했을 때 발생한다.
 *
 * <p>{@link BusinessException} 을 상속해 전역 핸들러가 별도 분기 없이
 * {@code 400 INSUFFICIENT_ASSET_QUANTITY} 로 응답하게 하면서도, 도메인 계층에서 의미가 드러나는 이름을 유지한다.
 */
public class InsufficientAssetQuantityException extends BusinessException {

  public static InsufficientAssetQuantityException forPosition(
      String name, String symbol, BigDecimal owned, BigDecimal requested) {
    return new InsufficientAssetQuantityException(
        "%s(%s) 보유 수량이 부족합니다. (보유: %s %s, 요청: %s %s)"
            .formatted(name, symbol, format(owned), symbol, format(requested), symbol));
  }

  public static InsufficientAssetQuantityException forCashBalance(
      String name, String currency, BigDecimal owned, BigDecimal requested) {
    return new InsufficientAssetQuantityException(
        "%s(%s) 잔액이 부족합니다. (보유: %s %s, 요청: %s %s)"
            .formatted(name, currency, format(owned), currency, format(requested), currency));
  }

  private InsufficientAssetQuantityException(String message) {
    super(ErrorCode.INSUFFICIENT_ASSET_QUANTITY, message);
  }

  private static String format(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }
}
