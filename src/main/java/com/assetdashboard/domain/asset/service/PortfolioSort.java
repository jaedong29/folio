package com.assetdashboard.domain.asset.service;

import com.assetdashboard.domain.asset.dto.PortfolioResponse;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

/**
 * Portfolio 조회의 정렬 조건 (PRD 4-5의 {@code sort=unrealizedPnl,desc}).
 *
 * <p>Spring Data 의 {@code Pageable} 을 쓰지 않고 직접 파싱한다. 정렬 대상인 평가손익·평가금액은 <b>DB 컬럼이
 * 아니라 조회 시점에 계산되는 값</b>이라 SQL 로 정렬할 수 없기 때문이다. 임의의 필드명을 그대로 받지 않고 허용된
 * 필드만 열어두어, 잘못된 값은 500 이 아니라 400 으로 응답하게 한다.
 */
public enum PortfolioSort {

  /** 평가손익 순. */
  UNREALIZED_PNL("unrealizedPnl", PortfolioResponse::unrealizedPnl),

  /** 평가금액 순. */
  VALUATION("valuationKRW", PortfolioResponse::valuationKRW),

  /** 실현손익 순. */
  REALIZED_PNL("realizedPnl", PortfolioResponse::realizedPnl);

  private static final String DEFAULT_EXPRESSION = "unrealizedPnl,desc";

  private final String field;
  private final Function<PortfolioResponse, BigDecimal> extractor;

  PortfolioSort(String field, Function<PortfolioResponse, BigDecimal> extractor) {
    this.field = field;
    this.extractor = extractor;
  }

  /**
   * {@code 필드,방향} 형식의 정렬 표현식을 해석한다.
   *
   * @param expression 정렬 표현식. null 이거나 비어 있으면 기본값 {@code unrealizedPnl,desc}
   * @return 해석된 정렬 조건
   * @throws BusinessException 지원하지 않는 필드명이면 {@code INVALID_INPUT}
   */
  public static SortSpec parse(String expression) {
    String raw =
        (expression == null || expression.isBlank()) ? DEFAULT_EXPRESSION : expression.trim();
    String[] parts = raw.split(",");
    String fieldName = parts[0].trim();
    boolean desc = parts.length < 2 || !"asc".equalsIgnoreCase(parts[1].trim());

    for (PortfolioSort candidate : values()) {
      if (candidate.field.equalsIgnoreCase(fieldName)) {
        return new SortSpec(candidate, desc);
      }
    }
    throw new BusinessException(
        ErrorCode.INVALID_INPUT,
        "지원하지 않는 정렬 기준입니다: %s".formatted(fieldName.toLowerCase(Locale.ROOT)));
  }

  /**
   * 해석된 정렬 조건.
   *
   * @param sort 정렬 기준 필드
   * @param descending 내림차순 여부
   */
  public record SortSpec(PortfolioSort sort, boolean descending) {

    /**
     * 실제 비교기를 만든다.
     *
     * <p>값이 {@code null} 인 자산(현재가를 확보하지 못해 평가손익을 계산할 수 없는 경우)은 정렬 방향과 무관하게
     * 항상 뒤로 보낸다. 화면 상단은 "확인된 숫자"가 차지해야 한다.
     *
     * @return Portfolio 비교기
     */
    public Comparator<PortfolioResponse> comparator() {
      Comparator<PortfolioResponse> byValue =
          (a, b) -> {
            BigDecimal left = sort.extractor.apply(a);
            BigDecimal right = sort.extractor.apply(b);
            if (left == null || right == null) {
              // null 은 방향과 무관하게 항상 뒤로 보낸다.
              return (left == null && right == null) ? 0 : (left == null ? 1 : -1);
            }
            return descending ? right.compareTo(left) : left.compareTo(right);
          };
      return byValue.thenComparing(PortfolioResponse::name);
    }
  }
}
