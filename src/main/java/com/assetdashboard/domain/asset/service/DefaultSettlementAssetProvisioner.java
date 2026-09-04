package com.assetdashboard.domain.asset.service;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 사용자가 매매대금 받을 곳을 따로 만들지 않도록 기본 투자 대기자금을 준비한다. */
@Service
@RequiredArgsConstructor
public class DefaultSettlementAssetProvisioner {

  private static final List<DefaultCash> DEFAULTS =
      List.of(
          new DefaultCash("KRW", "원화 대기자금"),
          new DefaultCash("USD", "달러 대기자금"),
          new DefaultCash("USDT", "테더 대기자금"));

  private final AssetRepository assetRepository;

  /**
   * KRW/USD/USDT CASH 자산 중 빠진 것만 0 잔액으로 만든다.
   *
   * <p>기존 사용자도 다음 Dashboard/Asset 조회에서 자동 보완되어야 하므로 독립된 짧은 트랜잭션으로 실행한다.
   * 이미 존재하는 자산의 이름과 잔액은 건드리지 않고, 삭제된 기본 자산만 복구한다.
   *
   * @param userId 사용자 id
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void ensureDefaults(Long userId) {
    for (DefaultCash definition : DEFAULTS) {
      assetRepository
          .findByUserIdAndTypeAndSymbol(userId, AssetType.CASH, definition.currency())
          .ifPresentOrElse(
              asset -> {
                if (asset.isDeleted()) {
                  asset.restore(asset.getName(), definition.currency());
                } else {
                  // 과거 API로 통화를 잘못 바꾼 데이터도 reserved symbol의 통화로 되돌린다.
                  asset.updateDisplayInfo(null, definition.currency());
                }
              },
              () ->
                  assetRepository.save(
                      Asset.create(
                          userId,
                          AssetType.CASH,
                          definition.currency(),
                          definition.name(),
                          definition.currency())));
    }
  }

  private record DefaultCash(String currency, String name) {}
}
