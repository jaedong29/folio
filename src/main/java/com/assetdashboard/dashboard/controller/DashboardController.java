package com.assetdashboard.dashboard.controller;

import com.assetdashboard.dashboard.dto.DashboardResponse;
import com.assetdashboard.dashboard.facade.DashboardFacade;
import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard API (PRD 4-4). 첫 화면이 필요로 하는 모든 값을 한 번의 요청으로 내려준다. */
@Tag(name = "Dashboard", description = "첫 화면 — 총자산 / 요약 / 배분 / 최근 거래")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardFacade dashboardFacade;

  /**
   * 대시보드를 조회한다.
   *
   * @param userId 인증된 사용자 id
   * @return 총자산·투자요약·현금요약·자산배분·최근거래
   */
  @Operation(
      summary = "Dashboard 조회",
      description = "화면 한 장에 필요한 값을 한 번에 반환한다. 시세 조회가 실패해도 마지막 저장값으로 폴백해 항상 응답한다.")
  @GetMapping
  public ResponseEntity<DashboardResponse> getDashboard(@CurrentUserId Long userId) {
    return ResponseEntity.ok(dashboardFacade.getDashboard(userId));
  }
}
