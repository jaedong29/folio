package com.assetdashboard.infra.price.fx;

import com.assetdashboard.infra.price.PriceProperties;
import com.assetdashboard.infra.price.PriceProviderException;
import com.assetdashboard.infra.price.crypto.UpbitExchangeRateClient;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * USD/KRW와 USDT/KRW 환율 조회의 단일 진입점.
 *
 * <p>환율도 시세와 마찬가지로 사용자별 데이터가 아닌 전역 사실이므로 통화 코드 단위로 메모리 캐시한다. 동일 통화의
 * 동시 호출은 하나로 모으고 외부 조회가 실패하면 만료된 마지막 성공값을 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxRateQueryService {

  /** USD/KRW 출처 표기. */
  public static final String USD_SOURCE = "Yahoo Finance KRW=X";

  /** USDT/KRW 출처 표기. */
  public static final String USDT_SOURCE = "Upbit KRW-USDT";

  private final YahooFinanceExchangeRateClient yahooClient;
  private final UpbitExchangeRateClient upbitClient;
  private final PriceProperties properties;

  private final Map<String, FxRateQuote> cache = new ConcurrentHashMap<>();
  private final Map<String, Object> inFlightLocks = new ConcurrentHashMap<>();

  /**
   * 여러 통화의 환율을 조회한다.
   *
   * @param currencies 통화 코드 목록
   * @param force true면 TTL과 무관하게 외부 조회를 시도한다
   * @return 사용 가능한 환율만 담긴 Map
   */
  public Map<String, FxRateQuote> getRates(Collection<String> currencies, boolean force) {
    Map<String, FxRateQuote> result = new HashMap<>();
    currencies.stream()
        .filter(currency -> currency != null && !currency.isBlank())
        .map(currency -> currency.trim().toUpperCase(Locale.ROOT))
        .distinct()
        .forEach(
            currency ->
                fetchWithFallback(currency, force)
                    .ifPresent(quote -> result.put(currency, quote)));
    return result;
  }

  /**
   * 통화 하나의 환율을 캐시 정책에 따라 조회한다.
   *
   * @param currency 통화 코드
   * @param force true면 캐시 TTL을 건너뛴다
   * @return 조회값 또는 마지막 성공값
   */
  public Optional<FxRateQuote> fetchWithFallback(String currency, boolean force) {
    String normalized = currency.trim().toUpperCase(Locale.ROOT);
    if ("KRW".equals(normalized)) {
      return Optional.of(FxRateQuote.of("KRW", BigDecimal.ONE, "KRW 기준 통화"));
    }
    if (!force) {
      FxRateQuote fresh = cache.get(normalized);
      if (fresh != null && !fresh.isExpired(properties.cacheTtlMinutes())) {
        return Optional.of(fresh);
      }
    }

    Object lock = inFlightLocks.computeIfAbsent(normalized, key -> new Object());
    synchronized (lock) {
      try {
        if (!force) {
          FxRateQuote refreshed = cache.get(normalized);
          if (refreshed != null && !refreshed.isExpired(properties.cacheTtlMinutes())) {
            return Optional.of(refreshed);
          }
        }
        FxRateQuote quote = fetchExternal(normalized);
        cache.put(normalized, quote);
        return Optional.of(quote);
      } catch (PriceProviderException e) {
        log.warn("[FX] {} 조회 실패 — 마지막 성공값으로 폴백: {}", normalized, e.getMessage());
        return Optional.ofNullable(cache.get(normalized));
      } finally {
        inFlightLocks.remove(normalized, lock);
      }
    }
  }

  /**
   * 마지막으로 확보한 값을 반환한다. 만료 여부는 검사하지 않는다.
   *
   * @param currency 통화 코드
   * @return 캐시에 저장된 마지막 환율
   */
  public Optional<FxRateQuote> getLastGood(String currency) {
    return Optional.ofNullable(cache.get(currency.trim().toUpperCase(Locale.ROOT)));
  }

  private FxRateQuote fetchExternal(String currency) {
    log.info("[FX] 외부 조회 시작 {}", currency);
    return switch (currency) {
      case "USD" -> FxRateQuote.of("USD", yahooClient.fetchKrwPerUsd(), USD_SOURCE);
      case "USDT" -> FxRateQuote.of("USDT", upbitClient.fetchKrwPerUsdt(), USDT_SOURCE);
      default ->
          throw new PriceProviderException(
              PriceProviderException.Kind.PROVIDER_UNAVAILABLE,
              "지원하지 않는 자동 환율 통화입니다: %s".formatted(currency));
    };
  }
}
