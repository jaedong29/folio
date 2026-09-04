package com.assetdashboard.dashboard.controller;

import com.assetdashboard.dashboard.dto.DemoHistorySeedResponse;
import com.assetdashboard.dashboard.snapshot.DemoPortfolioHistoryService;
import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영에는 노출되지 않는 local 발표 데이터 준비 API. */
@Profile("local")
@Tag(name = "Demo", description = "local 발표 데이터")
@RestController
@RequestMapping("/api/dashboard/demo-history")
@RequiredArgsConstructor
public class DemoDashboardController {

  private final DemoPortfolioHistoryService demoPortfolioHistoryService;

  @Operation(summary = "발표용 90일 순자산 이력 준비")
  @PostMapping
  public ResponseEntity<DemoHistorySeedResponse> seed(@CurrentUserId Long userId) {
    int created = demoPortfolioHistoryService.seed(userId);
    return ResponseEntity.ok(
        new DemoHistorySeedResponse(created, "발표용 순자산 이력을 준비했습니다."));
  }
}
