package com.assetdashboard.evidence.news;

import com.assetdashboard.evidence.agent.GroundedToolResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 등록 자산과 공용 뉴스 저장소를 연결하는 읽기 전용 Agent Tool. */
@Component
@RequiredArgsConstructor
public class NewsEvidenceToolAdapter {

  public static final String TOOL_NAME = "searchSymbolNews";

  private final NewsEvidenceService service;
  private final NewsEvidenceFactExtractor factExtractor;

  public NewsEvidenceToolResult execute(Long userId, Long assetId) {
    NewsEvidenceResponse response = service.getLatest(userId, assetId);
    List<String> references = new ArrayList<>();
    references.add("asset:" + assetId);
    response.items().forEach(item -> references.add("news:" + item.newsId()));
    GroundedToolResult grounding =
        new GroundedToolResult(
            TOOL_NAME,
            response.conclusion(),
            factExtractor.extract(response),
            references);
    return new NewsEvidenceToolResult(response, grounding);
  }
}
