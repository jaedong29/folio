package com.assetdashboard.infra.price;

import com.assetdashboard.infra.price.cache.PriceCache;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 시세 조회의 유일한 진입점. 캐시 확인 → 미스만 외부 조회 → 중복 호출 차단을 담당한다.
 *
 * <p>이 책임을 {@code AssetService} 에 두지 않은 이유는, 캐시·타임아웃·동시성은 도메인 로직이 아니라 조회
 * 인프라의 관심사이기 때문이다. {@code AssetService} 는 {@link #getPrices(Set)} 를 호출해 결과 Map 을 받아
 * 쓰기만 한다(PRD 6장).
 *
 * <p><b>이 클래스의 메서드는 절대 {@code @Transactional} 을 달지 않는다.</b> DB 트랜잭션이 열린 채로 수 초짜리
 * 네트워크 I/O 를 기다리면 커넥션 풀이 고갈된다. 호출부는 트랜잭션을 닫은 뒤에 이 서비스를 부른다(PRD 2장 제약 1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceQueryService {

  private final PriceCache priceCache;
  private final PriceProviderResolver resolver;

  /**
   * symbol 단위 락. 같은 심볼에 대한 동시 외부 호출을 한 건으로 모은다(thundering herd 방지).
   *
   * <p>사용자가 대시보드를 연타하거나 여러 사용자가 동시에 진입하면 같은 심볼을 중복 호출하게 된다. 이때 첫 번째
   * 호출만 외부로 나가고 나머지는 락을 기다렸다가 갱신된 캐시를 읽는다(PRD 2장 제약 3).
   */
  private final Map<SymbolKey, Object> inFlightLocks = new ConcurrentHashMap<>();

  /**
   * 여러 심볼의 시세를 한 번에 조회한다.
   *
   * <p>호출 전에 심볼 집합의 중복이 제거되어 있으므로, BTC 를 3개 자산에서 쓰더라도 외부 조회는 최대 1회다.
   *
   * @param keys 조회할 심볼 키 집합
   * @return 조회에 성공했거나 캐시에 있던 심볼만 담긴 Map. <b>실패한 심볼은 아예 포함되지 않는다</b> — 호출부가
   *     {@code assets.current_price} 로 폴백하도록 하기 위해 예외를 던지지 않는다
   */
  public Map<SymbolKey, PriceQuote> getPrices(Set<SymbolKey> keys) {
    return getPrices(keys, false);
  }

  /**
   * 여러 심볼의 시세를 조회한다.
   *
   * @param keys 조회할 심볼 키 집합
   * @param force true 면 TTL 안의 캐시도 건너뛰고 외부 조회를 시도한다
   * @return 사용 가능한 시세 Map
   */
  public Map<SymbolKey, PriceQuote> getPrices(Set<SymbolKey> keys, boolean force) {
    Map<SymbolKey, PriceQuote> result = new HashMap<>();
    for (SymbolKey key : keys) {
      fetchWithFallback(key, force).ifPresent(quote -> result.put(key, quote));
    }
    return result;
  }

  /**
   * 심볼 하나의 시세를 조회한다. 실패 시 만료된 캐시값으로 폴백한다.
   *
   * @param key 조회할 심볼 키
   * @return 사용 가능한 시세. 캐시에도 없고 조회도 실패하면 빈 Optional
   */
  public Optional<PriceQuote> fetchWithFallback(SymbolKey key) {
    return fetchWithFallback(key, false);
  }

  /**
   * 캐시 정책을 적용해 시세 하나를 조회한다.
   *
   * @param key 조회할 심볼 키
   * @param force true 면 첫 캐시 확인을 건너뛰고 외부 조회를 시도한다
   * @return 사용 가능한 시세. 외부 조회 실패 시 마지막 캐시값으로 폴백
   */
  public Optional<PriceQuote> fetchWithFallback(SymbolKey key, boolean force) {
    if (force) {
      return fetchForcedWithFallback(key);
    }
    Optional<PriceQuote> fresh = priceCache.getFresh(key);
    if (fresh.isPresent()) {
      return fresh;
    }

    Object lock = inFlightLocks.computeIfAbsent(key, k -> new Object());
    synchronized (lock) {
      // 락을 기다리는 동안 다른 스레드가 이미 갱신했을 수 있다. 다시 확인해 중복 호출을 막는다.
      Optional<PriceQuote> refreshed = priceCache.getFresh(key);
      if (refreshed.isPresent()) {
        return refreshed;
      }
      try {
        PriceQuote quote = fetchFromProvider(key);
        priceCache.put(key, quote);
        return Optional.of(quote);
      } catch (PriceProviderException e) {
        // 실패해도 예외를 전파하지 않는다. 만료된 캐시값이라도 있으면 그것을 쓴다.
        log.warn("[Price] 조회 실패 {} — 폴백 시도: {}", key, e.getMessage());
        return priceCache.getAny(key);
      } finally {
        inFlightLocks.remove(key, lock);
      }
    }
  }

  /** 강제 갱신은 심볼 락 안에서 외부 조회해 같은 심볼의 동시 요청을 직렬화한다. */
  private Optional<PriceQuote> fetchForcedWithFallback(SymbolKey key) {
    Object lock = inFlightLocks.computeIfAbsent(key, k -> new Object());
    synchronized (lock) {
      try {
        PriceQuote quote = fetchFromProvider(key);
        priceCache.put(key, quote);
        return Optional.of(quote);
      } catch (PriceProviderException e) {
        log.warn("[Price] 강제 조회 실패 {} — 마지막 캐시값으로 폴백: {}", key, e.getMessage());
        return priceCache.getAny(key);
      } finally {
        inFlightLocks.remove(key, lock);
      }
    }
  }

  /**
   * 캐시를 건너뛰고 외부에서 직접 조회한다. 자산 등록 시점의 심볼 검증에 사용한다.
   *
   * @param key 검증할 심볼 키
   * @return 조회된 시세
   * @throws PriceProviderException 조회에 실패하거나 존재하지 않는 심볼인 경우
   */
  public PriceQuote fetchDirect(SymbolKey key) {
    PriceQuote quote = fetchFromProvider(key);
    priceCache.put(key, quote);
    return quote;
  }

  private PriceQuote fetchFromProvider(SymbolKey key) {
    // 이 로그가 곧 "외부로 나간 호출"의 기록이다. 캐시가 실제로 동작하는지 확인하는 관측 지점.
    log.info("[Price] 외부 조회 시작 {}", key);
    PriceProvider provider =
        resolver
            .resolve(key.type())
            .orElseThrow(
                () ->
                    new PriceProviderException(
                        PriceProviderException.Kind.PROVIDER_UNAVAILABLE,
                        "자동 시세 조회 대상이 아닙니다: %s".formatted(key.type())));
    return provider.fetchPrice(key.symbol());
  }
}
