package com.assetdashboard.evidence.trend;

import com.assetdashboard.evidence.agent.GroundedToolResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 등록 자산의 최근 가격 방향을 서버 계산 결과로 제공하는 읽기 전용 Tool Adapter. */
@Component
@RequiredArgsConstructor
public class PriceTrendEvidenceToolAdapter {

  public static final String TOOL_NAME = "getPriceTrendEvidence";

  private final PriceTrendEvidenceService evidenceService;
  private final PriceTrendEvidenceFactExtractor factExtractor;

  public PriceTrendEvidenceToolResult execute(Long userId, Long assetId) {
    PriceTrendEvidenceResponse response =
        evidenceService.getPriceTrendEvidence(userId, assetId);
    GroundedToolResult grounding =
        new GroundedToolResult(
            TOOL_NAME,
            response.conclusion(),
            factExtractor.extract(response),
            List.of("asset:" + assetId, "price-trend:" + response.traceId()));
    return new PriceTrendEvidenceToolResult(response, grounding);
  }
}
