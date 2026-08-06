package com.assetdashboard.domain.asset.service;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.infra.price.PriceQuote;
import com.assetdashboard.infra.price.SymbolKey;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부에서 조회한 시세를 Asset 에 반영하는 <b>별도 트랜잭션</b> 경계.
 *
 * <p>별도 클래스로 뽑은 이유는 두 가지다.
 *
 * <ol>
 *   <li>같은 클래스 안의 {@code @Transactional} 메서드를 자기 자신이 호출하면 프록시를 타지 않아 트랜잭션이 걸리지
 *       않는다. 조회 흐름(트랜잭션 밖)과 반영(트랜잭션 안)을 확실히 분리하려면 빈이 달라야 한다.
 *   <li>"외부 호출은 트랜잭션 밖에서, 반영만 짧은 트랜잭션 안에서"라는 규칙을 클래스 경계로 드러내기 위함이다
 *       (PRD 2장 제약 1).
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceRefreshService {

  private final AssetRepository assetRepository;

  /**
   * 조회된 시세를 자산에 반영하고, 갱신된 자산을 다시 읽어 돌려준다.
   *
   * <p>{@code REQUIRES_NEW} 로 항상 새 트랜잭션을 열어, 호출부가 어떤 트랜잭션 상태이든 이 갱신만 짧게 커밋되도록
   * 한다. 시세 갱신이 실패해도 조회 자체는 계속되어야 하므로 롤백 범위도 여기로 좁힌다.
   *
   * @param assetIds 갱신 대상 자산 id (소유권이 이미 검증된 값)
   * @param quotes 심볼별 조회 결과
   * @return 갱신이 반영된 자산 목록
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Asset> applyQuotes(List<Long> assetIds, Map<SymbolKey, PriceQuote> quotes) {
    List<Asset> assets = assetRepository.findAllById(assetIds);
    if (quotes.isEmpty()) {
      return assets;
    }

    for (Asset asset : assets) {
      PriceQuote quote = quotes.get(new SymbolKey(asset.getType(), asset.getSymbol()));
      if (quote == null) {
        continue;
      }
      asset.updateCurrentPrice(quote.price(), AssetSource.API);
      if (quote.exchangeRate() != null) {
        // CRYPTO 만 환율까지 자동 조회된다. 해외주식의 환율은 수동 입력이므로 덮어쓰지 않는다.
        asset.updateExchangeRate(quote.exchangeRate());
      }
    }
    return assets;
  }
}
