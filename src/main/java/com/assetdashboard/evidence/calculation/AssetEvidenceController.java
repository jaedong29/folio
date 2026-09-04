package com.assetdashboard.evidence.calculation;

import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 금융 Agent가 사용할 읽기 전용 계산 근거 API. */
@Tag(name = "Financial Evidence", description = "LLM 답변 전에 조회하는 금융 계산 근거")
@RestController
@RequestMapping("/api/ai/evidence/assets")
@RequiredArgsConstructor
public class AssetEvidenceController {

  private final AssetEvidenceService assetEvidenceService;

  @Operation(
      summary = "자산 계산 근거 조회",
      description = "현재 계산값, 가격·환율 상태, 최근 거래와 누락 경고를 구조화해서 반환한다.")
  @GetMapping("/{assetId}")
  public ResponseEntity<AssetEvidenceResponse> getAssetEvidence(
      @CurrentUserId Long userId, @PathVariable Long assetId) {
    return ResponseEntity.ok(assetEvidenceService.getAssetEvidence(userId, assetId));
  }
}
