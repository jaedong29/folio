package com.assetdashboard.infra.price.cache;

import com.assetdashboard.infra.price.PriceProperties;
import com.assetdashboard.infra.price.PriceQuote;
import com.assetdashboard.infra.price.SymbolKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link ConcurrentHashMap} 기반 인메모리 시세 캐시.
 *
 * <p>별도 테이블이나 Redis 를 두지 않은 이유는 캐시가 15분이면 만료되는 휘발성 데이터이기 때문이다. 서버가
 * 재시작되어 캐시가 비어도 {@code assets.current_price} 에 마지막 성공값이 남아 있어 데이터 손실이 아니다.
 * 이 규모에서 저장소를 하나 더 두는 것은 과설계다(PRD 3장).
 */
@Component
@RequiredArgsConstructor
public class InMemoryPriceCache implements PriceCache {

  private final Map<SymbolKey, PriceQuote> store = new ConcurrentHashMap<>();
  private final PriceProperties properties;

  @Override
  public Optional<PriceQuote> getFresh(SymbolKey key) {
    return getAny(key).filter(quote -> !quote.isExpired(properties.cacheTtlMinutes()));
  }

  @Override
  public Optional<PriceQuote> getAny(SymbolKey key) {
    return Optional.ofNullable(store.get(key));
  }

  @Override
  public void put(SymbolKey key, PriceQuote quote) {
    store.put(key, quote);
  }

  @Override
  public void clear() {
    store.clear();
  }
}
