package com.assetdashboard.infra.price.cache;

import com.assetdashboard.infra.price.PriceQuote;
import com.assetdashboard.infra.price.SymbolKey;
import java.util.Optional;

/**
 * 전역 시세 캐시.
 *
 * <p>인터페이스로 분리해 둔 이유는 확장 지점이 명확하기 때문이다. MVP 는 단일 인스턴스라 애플리케이션 메모리로
 * 충분하지만, 인스턴스가 2대 이상이 되면 인스턴스별로 캐시가 따로 돌아 외부 호출이 배수로 늘어난다. 그 시점에
 * Redis 구현체로 교체하면 되고 호출부는 바뀌지 않는다(PRD 3장, Roadmap 7-2).
 */
public interface PriceCache {

  /**
   * 유효한(만료되지 않은) 캐시값을 가져온다.
   *
   * @param key 조회할 심볼 키
   * @return 유효한 값이 있으면 시세, 없거나 만료되었으면 빈 Optional
   */
  Optional<PriceQuote> getFresh(SymbolKey key);

  /**
   * 만료 여부와 무관하게 캐시값을 가져온다.
   *
   * <p>외부 조회에 실패했을 때 "만료되었지만 있는 값"으로 폴백하기 위한 경로다. 틀린 숫자를 보여주지 않기 위해
   * 호출부는 이 값을 {@code priceStale} 로 표시한다.
   *
   * @param key 조회할 심볼 키
   * @return 값이 있으면 시세 (만료되었을 수 있음)
   */
  Optional<PriceQuote> getAny(SymbolKey key);

  /**
   * 조회한 시세를 캐시에 저장한다.
   *
   * @param key 심볼 키
   * @param quote 저장할 시세
   */
  void put(SymbolKey key, PriceQuote quote);

  /** 캐시를 비운다. 데모에서 강제 재조회를 보여줄 때 사용한다. */
  void clear();
}
