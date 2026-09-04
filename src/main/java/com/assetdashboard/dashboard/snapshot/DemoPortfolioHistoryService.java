package com.assetdashboard.dashboard.snapshot;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** local 프로필에서만 발표용 순자산 곡선을 준비한다. */
@Profile("local")
@Service
@RequiredArgsConstructor
public class DemoPortfolioHistoryService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final AssetService assetService;
  private final PortfolioSnapshotWriter snapshotWriter;

  public int seed(Long userId) {
    List<Asset> assets = assetService.getActiveAssetsWithFreshPrice(userId);
    BigDecimal total =
        assets.stream()
            .map(assetService::valuationOrZero)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    if (total.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "먼저 평가 가능한 데모 자산을 준비해주세요.");
    }
    return snapshotWriter.seedDemoHistory(userId, total, LocalDate.now(KST));
  }
}
