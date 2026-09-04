package com.assetdashboard.evidence.document;

import com.assetdashboard.evidence.agent.GroundedToolResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 등록 자산과 사용자가 직접 연결한 근거 자료 저장소를 연결하는 읽기 전용 Agent Tool. */
@Component
@RequiredArgsConstructor
public class SymbolEvidenceToolAdapter {

  public static final String TOOL_NAME = "searchSymbolEvidence";

  private final SymbolEvidenceService service;
  private final SymbolEvidenceFactExtractor factExtractor;

  public SymbolEvidenceToolResult execute(Long userId, Long assetId) {
    SymbolEvidenceResponse response = service.getEvidence(userId, assetId);
    List<String> references = new ArrayList<>();
    references.add("asset:" + assetId);
    response.items().forEach(item -> references.add("evidence:" + item.documentId()));
    GroundedToolResult grounding =
        new GroundedToolResult(
            TOOL_NAME, response.conclusion(), factExtractor.extract(response), references);
    return new SymbolEvidenceToolResult(response, grounding);
  }
}
