package com.assetdashboard.domain.asset.dto;

import com.assetdashboard.domain.asset.entity.AssetType;
import java.math.BigDecimal;

/**
 * 자산 배분 한 조각 (Pie Chart 용, PRD 4-4).
 *
 * <p>종목 단위가 아니라 <b>type 단위</b>로만 집계한다. 종목 단위 Allocation 은 Dashboard 응답을 무겁게 만들어
 * Roadmap 7-5 로 미뤘다.
 *
 * @param type 자산 종류
 * @param valuationKRW 해당 종류의 평가금액 합계 (KRW)
 * @param ratio 전체 자산 대비 비율(%)
 */
public record AllocationResponse(AssetType type, BigDecimal valuationKRW, BigDecimal ratio) {}
