package com.assetdashboard.evidence.calculation;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 금융 계산의 입력값·공식·최근 거래를 한 번에 반환하는 읽기 전용 Agent 근거 계약.
 *
 * <p>자연어 답변보다 먼저 이 구조화된 값을 만들면 LLM이 계산값이나 출처를 새로 지어낼 여지를 줄일 수 있다.
 */
public record AssetEvidenceResponse(
    String traceId,
    LocalDateTime generatedAt,
    EvidenceConclusion conclusion,
    AssetCalculation calculation,
    PriceEvidence priceEvidence,
    ExchangeRateEvidence exchangeRateEvidence,
    List<EvidenceWarning> warnings,
    TransactionEvidence transactions) {

  /** Agent가 설명에 사용할 현재 자산 상태와 계산 결과. */
  public record AssetCalculation(
      Long assetId,
      AssetType type,
      String symbol,
      String displaySymbol,
      String name,
      String currency,
      BigDecimal quantity,
      BigDecimal averagePriceKrw,
      BigDecimal averagePriceOriginal,
      BigDecimal valuationKrw,
      BigDecimal unrealizedPnlKrw,
      BigDecimal unrealizedPnlRate,
      BigDecimal realizedPnlKrw,
      String valuationRule,
      String unrealizedPnlRule) {

    public static AssetCalculation from(Asset asset) {
      return new AssetCalculation(
          asset.getId(),
          asset.getType(),
          asset.getSymbol(),
          asset.getDisplaySymbol(),
          asset.getName(),
          asset.getCurrency(),
          asset.getQuantity(),
          asset.getAvgPrice(),
          asset.getAvgPriceOriginal(),
          asset.getValuation(),
          asset.getUnrealizedPnl(),
          asset.getPnlRate(),
          asset.getRealizedPnl(),
          "quantity * currentPrice * exchangeRate",
          "valuationKrw - (quantity * averagePriceKrw)");
    }
  }

  /** 현재가의 값·출처·시각. */
  public record PriceEvidence(
      BigDecimal value,
      AssetSource source,
      LocalDateTime updatedAt,
      EvidenceValueStatus status) {}

  /** 현재 환율의 값·시각. 기존 모델이 환율 제공자까지 저장하지 않으므로 출처를 추측하지 않는다. */
  public record ExchangeRateEvidence(
      BigDecimal value, LocalDateTime updatedAt, EvidenceValueStatus status) {}

  /** 계산을 제한하는 명시적인 경고. */
  public record EvidenceWarning(String code, String message) {}

  /** 전체 건수를 함께 노출해 최근 거래 일부만 보고 전체 이력이라고 오인하지 않게 한다. */
  public record TransactionEvidence(
      long totalCount, int returnedCount, boolean truncated, List<TransactionItem> items) {}

  /** 메모처럼 불필요한 개인정보는 제외한 거래 근거. */
  public record TransactionItem(
      Long transactionId,
      TransactionType type,
      BigDecimal quantity,
      BigDecimal price,
      BigDecimal exchangeRate,
      Long settlementAssetId,
      BigDecimal settlementAmount,
      LocalDateTime tradedAt) {

    public static TransactionItem from(Transaction transaction) {
      return new TransactionItem(
          transaction.getId(),
          transaction.getType(),
          transaction.getQuantity(),
          transaction.getPrice(),
          transaction.getExchangeRate(),
          transaction.getSettlementAssetId(),
          transaction.getSettlementAmount(),
          transaction.getTradedAt());
    }
  }
}
