package com.assetdashboard.infra.price.history;

import java.math.BigDecimal;

/** 시장가격 차트의 일별 종가 한 점. */
public record PriceHistoryPoint(long timestamp, BigDecimal price) {}
