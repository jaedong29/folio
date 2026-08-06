package com.assetdashboard.infra.price;

import com.assetdashboard.domain.asset.entity.AssetType;

/**
 * 외부 시세 조회 창구.
 *
 * <p>도메인 계층은 이 인터페이스만 알고 Yahoo Finance·Binance·Upbit 같은 구체적인 구현을 알지 못한다. 유료/공식
 * API(TwelveData 등)로 전환할 때 구현체만 추가하고 {@link PriceProviderResolver} 의 매핑을 바꾸면 되며,
 * {@code domain} 패키지는 손대지 않는다(Roadmap 7-2).
 */
public interface PriceProvider {

  /**
   * 이 구현체가 처리할 수 있는 자산 종류인지 답한다.
   *
   * <p>한 인터페이스에 구현체가 둘 이상이면 Spring 이 타입에 따라 알아서 주입해주지 않는다. 그래서 각 구현체가
   * 스스로 담당 범위를 선언하고, {@link PriceProviderResolver} 가 이를 읽어 매핑을 만든다.
   *
   * @param type 자산 종류
   * @return 처리 가능하면 {@code true}
   */
  boolean supports(AssetType type);

  /**
   * 심볼의 현재 시세를 조회한다.
   *
   * @param symbol 정규화된 심볼
   * @return 조회된 시세
   * @throws PriceProviderException 조회에 실패했거나 존재하지 않는 심볼인 경우
   */
  PriceQuote fetchPrice(String symbol);
}
