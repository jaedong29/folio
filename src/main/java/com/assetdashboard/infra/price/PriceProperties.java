package com.assetdashboard.infra.price;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시세 조회 관련 설정.
 *
 * @param cacheTtlMinutes 전역 시세 캐시의 TTL(분). 이 값은 응답의 {@code priceStale} 판정 기준이기도 하다
 * @param timeoutMillis 외부 API 의 connect/read 타임아웃(밀리초). 외부 API 가 느려도 대시보드가 멈추지 않게 한다
 * @param externalEnabled {@code false} 면 모든 외부 호출을 즉시 실패시킨다. PRD 8-3 시나리오 8(외부 시세 API
 *     강제 실패)을 재현하기 위한 스위치이며, 발표 데모에서 폴백 동작을 보여줄 때도 사용한다
 */
@ConfigurationProperties(prefix = "app.price")
public record PriceProperties(long cacheTtlMinutes, int timeoutMillis, boolean externalEnabled) {}
