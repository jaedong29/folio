package com.assetdashboard.infra.price.history;

import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.global.resilience.ExternalCallResilience;
import com.assetdashboard.global.resilience.ExternalSource;
import com.assetdashboard.infra.price.PriceProperties;
import com.assetdashboard.infra.price.SymbolKey;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 자산 상세용 최근 7일 시장가격을 조회한다.
 *
 * <p>Portfolio Snapshot 차트와는 다른 읽기 전용 시장 참고 정보다. 현재가 조회와 같은 TTL 및 마지막 성공값
 * 정책을 적용하고, 조회 실패가 자산 상세 전체를 막지 않도록 빈 결과를 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryQueryService {

  private static final String YAHOO_URL =
      "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=7d";
  private static final String BINANCE_URL =
      "https://api.binance.com/api/v3/klines?symbol={pair}&interval=1d&limit=7";

  private final RestClient priceRestClient;
  private final PriceProperties properties;
  private final ExternalCallResilience resilience;
  private final Map<SymbolKey, PriceHistoryQuote> cache = new ConcurrentHashMap<>();
  private final Map<SymbolKey, Object> locks = new ConcurrentHashMap<>();

  /**
   * 최근 일별 종가를 반환한다. 외부 조회 실패 시 마지막 성공값을 stale 상태로 돌려준다.
   *
   * @param type STOCK 또는 CRYPTO
   * @param symbol 내부 provider 심볼
   * @return 가격 시계열. 한 번도 조회하지 못했으면 points가 비어 있다
   */
  public PriceHistoryQuote getHistory(AssetType type, String symbol) {
    String source = sourceFor(type);
    if (!type.isInvestment()) {
      return unavailable(source);
    }

    SymbolKey key = new SymbolKey(type, symbol);
    PriceHistoryQuote cached = cache.get(key);
    if (cached != null && !cached.isExpired(properties.cacheTtlMinutes())) {
      return cached;
    }

    Object lock = locks.computeIfAbsent(key, ignored -> new Object());
    synchronized (lock) {
      cached = cache.get(key);
      if (cached != null && !cached.isExpired(properties.cacheTtlMinutes())) {
        return cached;
      }
      try {
        if (!properties.externalEnabled()) {
          throw new IllegalStateException("외부 시세 조회 비활성화");
        }
        ExternalSource externalSource =
            type == AssetType.STOCK ? ExternalSource.YAHOO_FINANCE : ExternalSource.BINANCE;
        List<PriceHistoryPoint> points =
            resilience.executeRead(
                externalSource,
                () -> type == AssetType.STOCK ? fetchYahoo(symbol) : fetchBinance(symbol));
        if (points.isEmpty()) {
          throw new IllegalStateException("가격 시계열이 비어 있음");
        }
        PriceHistoryQuote quote =
            new PriceHistoryQuote(source, LocalDateTime.now(), false, points);
        cache.put(key, quote);
        return quote;
      } catch (Exception e) {
        log.warn("가격 차트 조회 실패 key={}: {}", key, e.getMessage());
        PriceHistoryQuote lastGood = cache.get(key);
        return lastGood == null ? unavailable(source) : lastGood.asStale();
      }
    }
  }

  private List<PriceHistoryPoint> fetchYahoo(String symbol) {
    JsonNode body = priceRestClient.get().uri(YAHOO_URL, symbol).retrieve().body(JsonNode.class);
    JsonNode result = body == null ? null : body.path("chart").path("result").path(0);
    if (result == null || result.isMissingNode() || result.isNull()) {
      return List.of();
    }

    JsonNode timestamps = result.path("timestamp");
    JsonNode closes = result.path("indicators").path("quote").path(0).path("close");
    int count = Math.min(timestamps.size(), closes.size());
    List<PriceHistoryPoint> points = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      JsonNode close = closes.path(index);
      if (!close.isNull() && !close.isMissingNode() && close.isNumber()) {
        points.add(
            new PriceHistoryPoint(
                timestamps.path(index).asLong() * 1000L, close.decimalValue()));
      }
    }
    return points;
  }

  private List<PriceHistoryPoint> fetchBinance(String symbol) {
    String pair = symbol + "USDT";
    JsonNode body = priceRestClient.get().uri(BINANCE_URL, pair).retrieve().body(JsonNode.class);
    if (body == null || !body.isArray()) {
      return List.of();
    }

    List<PriceHistoryPoint> points = new ArrayList<>(body.size());
    for (JsonNode candle : body) {
      if (candle.isArray() && candle.size() > 4) {
        points.add(
            new PriceHistoryPoint(
                candle.path(0).asLong(), new BigDecimal(candle.path(4).asText())));
      }
    }
    return points;
  }

  /**
   * local 프로필의 실제 NIM 평가 fixture가 Yahoo·Binance를 실제로 호출하지 않고 결정적인 가격 이력을 준비할
   * 때만 쓴다. 캐시를 직접 채우므로 이후 {@link #getHistory}는 TTL이 지나기 전까지 이 값을 그대로 반환한다.
   */
  public void seed(AssetType type, String symbol, PriceHistoryQuote quote) {
    cache.put(new SymbolKey(type, symbol), quote);
  }

  private String sourceFor(AssetType type) {
    return type == AssetType.CRYPTO ? "Binance Spot · 1D" : "Yahoo Finance · 1D";
  }

  private PriceHistoryQuote unavailable(String source) {
    return new PriceHistoryQuote(source, null, true, List.of());
  }
}
