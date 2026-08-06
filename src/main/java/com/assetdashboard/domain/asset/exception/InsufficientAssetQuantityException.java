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

  /**
   * 보유 수량과 요청 수량을 메시지에 담아 예외를 생성한다.
   *
   * @param owned 현재 보유 수량
   * @param requested 요청한 수량
   */
  public InsufficientAssetQuantityException(BigDecimal owned, BigDecimal requested) {
    super(
        ErrorCode.INSUFFICIENT_ASSET_QUANTITY,
        "보유 수량이 부족합니다. (보유: %s, 요청: %s)"
            .formatted(owned.stripTrailingZeros().toPlainString(),
                requested.stripTrailingZeros().toPlainString()));
  }
}
