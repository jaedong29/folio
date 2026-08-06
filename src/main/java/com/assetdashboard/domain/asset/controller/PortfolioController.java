package com.assetdashboard.domain.asset.controller;

import com.assetdashboard.domain.asset.dto.PortfolioResponse;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portfolio API (PRD 4-5).
 *
 * <p>Portfolio 는 Entity 가 아니라 Asset 을 투자 관점에서 본 <b>View</b> 다. 그래서 별도 도메인 패키지를 만들지
 * 않고 {@code domain.asset} 안에 둔다.
 */
@Tag(name = "Portfolio", description = "투자 자산 목록 (평가손익 포함)")
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

  private final AssetService assetService;

  /**
   * 투자 자산 목록을 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @param type 필터링할 자산 종류. 생략하면 STOCK + CRYPTO 전체
   * @param sort {@code 필드,방향} 형식의 정렬 조건
   * @return 정렬된 Portfolio 목록
   */
  @Operation(
      summary = "Portfolio 조회",
      description =
          "STOCK/CRYPTO 만 반환한다. 조회 시점에 시세 캐시를 확인해 만료된 symbol 만 외부에서 갱신하며, "
              + "실패하면 마지막 저장값으로 폴백하고 priceStale=true 로 알린다.")
  @GetMapping
  public ResponseEntity<List<PortfolioResponse>> getPortfolio(
      @CurrentUserId Long userId,
      @Parameter(description = "STOCK 또는 CRYPTO. 생략 시 전체") @RequestParam(required = false)
          AssetType type,
      @Parameter(description = "정렬 조건. unrealizedPnl / valuationKRW / realizedPnl + asc|desc")
          @RequestParam(required = false, defaultValue = "unrealizedPnl,desc")
          String sort) {
    return ResponseEntity.ok(assetService.getPortfolio(userId, type, sort));
  }
}
