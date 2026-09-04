package com.assetdashboard.evidence.tool;

import com.assetdashboard.evidence.agent.EvidenceConclusionPolicy;
import com.assetdashboard.evidence.agent.GroundedToolResult;
import com.assetdashboard.evidence.calculation.AssetEvidenceResponse;
import com.assetdashboard.evidence.calculation.AssetEvidenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent가 사용할 읽기 전용 계산 Tool Adapter.
 *
 * <p>userId는 모델 인자가 아니라 인증 컨텍스트에서 애플리케이션이 전달한다.
 */
@Component
@RequiredArgsConstructor
public class AssetEvidenceToolAdapter {

  public static final String TOOL_NAME = "getAssetEvidence";

  private final AssetEvidenceService assetEvidenceService;
  private final AssetEvidenceFactExtractor factExtractor;
  private final EvidenceConclusionPolicy conclusionPolicy;

  public AssetEvidenceToolResult execute(Long userId, Long assetId) {
    AssetEvidenceResponse response =
        assetEvidenceService.getAssetEvidence(userId, assetId);
    GroundedToolResult grounding =
        new GroundedToolResult(
            TOOL_NAME,
            conclusionPolicy.fromAssetEvidence(response),
            factExtractor.extract(response),
            List.of("asset:" + assetId, "evidence-trace:" + response.traceId()));
    return new AssetEvidenceToolResult(response, grounding);
  }
}
