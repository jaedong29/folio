package com.assetdashboard.evidence.agent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영 중 NIM 비용을 눈으로 확인하기 위한 오늘자 사용량 조회 API. */
@Tag(name = "AI Usage", description = "하루 NIM 호출·토큰 사용량과 예산")
@RestController
@RequestMapping("/api/ai/usage")
@RequiredArgsConstructor
public class AiUsageController {

  private final LlmUsageBudgetService budgetService;
  private final FinancialAgentProperties properties;

  @Operation(
      summary = "오늘 NIM 사용량 조회",
      description = "Financial Evidence Agent와 News 요약이 공유하는 하루 호출 수·토큰 사용량과 설정된 예산을 반환한다.")
  @GetMapping("/today")
  public ResponseEntity<AiUsageResponse> today() {
    return ResponseEntity.ok(AiUsageResponse.of(budgetService.getTodayUsage(), properties));
  }
}
