package com.assetdashboard.dashboard.controller;

import com.assetdashboard.dashboard.dto.DashboardResponse;
import com.assetdashboard.dashboard.dto.PortfolioHistoryResponse;
import com.assetdashboard.dashboard.facade.DashboardFacade;
import com.assetdashboard.dashboard.snapshot.PortfolioHistoryService;
import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/** Dashboard API (PRD 4-4). 첫 화면이 필요로 하는 모든 값을 한 번의 요청으로 내려준다. */
@Tag(name = "Dashboard", description = "첫 화면 — 총자산 / 요약 / 배분 / 최근 거래")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Validated
public class DashboardController {

  private final DashboardFacade dashboardFacade;
  private final PortfolioHistoryService portfolioHistoryService;

  /**
   * 대시보드를 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @param force true 면 캐시 TTL과 무관하게 외부 시세 조회를 시도한다
   * @return 총자산·투자요약·현금요약·자산배분·최근거래
   */
  @Operation(
      summary = "Dashboard 조회",
      description = "화면 한 장에 필요한 값을 한 번에 반환한다. 시세 조회가 실패해도 마지막 저장값으로 폴백해 항상 응답한다.")
  @GetMapping
  public ResponseEntity<DashboardResponse> getDashboard(
      @CurrentUserId Long userId,
      @RequestParam(defaultValue = "false") boolean force) {
    return ResponseEntity.ok(dashboardFacade.getDashboard(userId, force));
  }

  /**
   * Asset Analysis의 순자산 추이를 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @param days 조회할 일수 (1~365)
   * @return 저장된 Portfolio Snapshot 이력
   */
  @Operation(
      summary = "Portfolio 순자산 이력 조회",
      description = "입출금을 포함한 날짜별 실제 총 자산 기준값을 반환한다. 투자 수익률이 아닌 순자산 추이용 데이터다.")
  @GetMapping("/history")
  public ResponseEntity<PortfolioHistoryResponse> getPortfolioHistory(
      @CurrentUserId Long userId,
      @RequestParam(defaultValue = "90") @Min(1) @Max(365) int days) {
    return ResponseEntity.ok(portfolioHistoryService.getHistory(userId, days));
  }
}
