package com.assetdashboard.infra.price;

import com.assetdashboard.domain.asset.entity.AssetType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 자산 종류에 맞는 {@link PriceProvider} 를 찾아준다.
 *
 * <p>{@code List<PriceProvider>} 를 주입받아 시작 시점에 {@code AssetType → PriceProvider} 매핑을 한 번만
 * 구성한다. 요청마다 리스트를 순회하며 {@code supports()} 를 부르는 방식보다 명시적이고, 어떤 타입에 어떤 구현체가
 * 걸려 있는지 기동 로그로 확인할 수 있다.
 */
@Slf4j
@Component
public class PriceProviderResolver {

  private final Map<AssetType, PriceProvider> providers = new EnumMap<>(AssetType.class);

  /**
   * 등록된 구현체들을 자산 종류별로 매핑한다.
   *
   * @param priceProviders 컨텍스트에 등록된 모든 시세 조회 구현체
   */
  public PriceProviderResolver(List<PriceProvider> priceProviders) {
    for (AssetType type : AssetType.values()) {
      priceProviders.stream()
          .filter(provider -> provider.supports(type))
          .findFirst()
          .ifPresent(provider -> providers.put(type, provider));
    }
    log.info(
        "[PriceProvider] 매핑 구성 완료: {}",
        providers.entrySet().stream()
            .map(e -> e.getKey() + "->" + e.getValue().getClass().getSimpleName())
            .toList());
  }

  /**
   * 자산 종류에 대응하는 구현체를 찾는다.
   *
   * @param type 자산 종류
   * @return 구현체. 자동 조회 대상이 아닌 종류(CASH/BANK)면 빈 Optional
   */
  public Optional<PriceProvider> resolve(AssetType type) {
    return Optional.ofNullable(providers.get(type));
  }
}
