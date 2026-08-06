package com.assetdashboard.domain.asset.service;

import com.assetdashboard.domain.asset.dto.AssetCreateRequest;
import com.assetdashboard.domain.asset.dto.AssetCreationResult;
import com.assetdashboard.domain.asset.dto.AssetResponse;
import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetSource;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.infra.price.PriceQuote;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검증을 마친 자산 등록을 실제로 저장하는 <b>트랜잭션 경계</b>.
 *
 * <p>{@link AssetService#create} 는 심볼 검증을 위해 외부 HTTP 를 호출하므로 트랜잭션 밖에서 돌아야 한다. 같은
 * 클래스 안의 {@code @Transactional} 메서드를 자기 자신이 호출하면 프록시를 타지 않아 트랜잭션이 걸리지 않으므로,
 * 저장 부분만 별도 빈으로 분리했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetRegistrar {

  private final AssetRepository assetRepository;

  /**
   * 자산을 새로 저장하거나, 삭제되었던 동일 심볼 자산을 되살린다.
   *
   * @param userId 인증된 사용자 id
   * @param request 등록 요청
   * @param symbol 정규화된 심볼
   * @param initialQuote 등록 시점에 확보한 시세. 없으면 {@code null}
   * @return 등록 결과 (신규 생성인지 복구인지 포함)
   */
  @Transactional
  public AssetCreationResult persist(
      Long userId, AssetCreateRequest request, String symbol, PriceQuote initialQuote) {

    Optional<Asset> existing =
        assetRepository.findByUserIdAndTypeAndSymbol(userId, request.type(), symbol);
    boolean restored = existing.isPresent();

    Asset asset =
        existing
            .map(
                found -> {
                  found.restore(request.resolvedName(), request.normalizedCurrency());
                  log.info(
                      "[Asset] 삭제되었던 자산을 복구 assetId={} userId={} symbol={}",
                      found.getId(),
                      userId,
                      symbol);
                  return found;
                })
            .orElseGet(
                () ->
                    assetRepository.save(
                        Asset.create(
                            userId,
                            request.type(),
                            symbol,
                            request.resolvedName(),
                            request.normalizedCurrency())));

    if (initialQuote != null) {
      // 등록 시점에 확보한 가격을 초기 현재가로 남긴다. 첫 대시보드 진입에서 0원으로 보이지 않게 하기 위함이다.
      asset.updateCurrentPrice(initialQuote.price(), AssetSource.API);
      if (initialQuote.exchangeRate() != null) {
        // CRYPTO 는 Upbit 에서 환율까지 함께 얻는다. 해외주식의 환율은 수동 입력이므로 여기서 채워지지 않는다.
        asset.updateExchangeRate(initialQuote.exchangeRate());
      }
    }
    return new AssetCreationResult(AssetResponse.from(asset), restored);
  }
}
