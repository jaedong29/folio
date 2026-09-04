package com.assetdashboard.domain.transaction.exception;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.math.BigDecimal;

/** 매수 또는 거래 삭제 정산에 필요한 투자 대기자금의 잔액이 부족할 때 발생한다. */
public class InsufficientSettlementFundsException extends BusinessException {

  public InsufficientSettlementFundsException(Asset settlementAsset, BigDecimal requested) {
    super(
        ErrorCode.INSUFFICIENT_SETTLEMENT_FUNDS,
        "%s(%s) 잔액이 부족합니다. (보유: %s %s, 필요: %s %s)"
            .formatted(
                settlementAsset.getName(),
                settlementAsset.getSymbol(),
                format(settlementAsset.getQuantity()),
                settlementAsset.getCurrency(),
                format(requested),
                settlementAsset.getCurrency()));
  }

  private static String format(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }
}
