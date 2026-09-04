package com.assetdashboard.evidence.trend;

import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 모델을 호출하지 않고 서버가 계산한 가격 방향 근거를 확인하는 읽기 전용 API. */
@Tag(name = "Financial Evidence", description = "LLM 답변 전에 조회하는 금융 계산 근거")
@RestController
@RequestMapping("/api/ai/evidence/assets")
@RequiredArgsConstructor
public class PriceTrendEvidenceController {

  private final PriceTrendEvidenceService evidenceService;

  @Operation(
      summary = "최근 가격 방향 근거 조회",
      description = "최근 7개 일별 가격의 변화율을 서버가 계산하고 ±2% 규칙으로 방향을 판정한다.")
  @GetMapping("/{assetId}/price-trend")
  public ResponseEntity<PriceTrendEvidenceResponse> getPriceTrendEvidence(
      @CurrentUserId Long userId, @PathVariable Long assetId) {
    return ResponseEntity.ok(evidenceService.getPriceTrendEvidence(userId, assetId));
  }
}
