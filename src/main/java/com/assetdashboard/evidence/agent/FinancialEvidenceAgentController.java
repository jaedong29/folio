package com.assetdashboard.evidence.agent;

import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 사용자의 자산 하나를 읽기 전용으로 설명하는 금융 Evidence Agent API. */
@Tag(name = "Financial Evidence Agent", description = "NIM Tool Calling 기반 근거 답변")
@RestController
@RequestMapping("/api/ai/agent/assets")
@RequiredArgsConstructor
public class FinancialEvidenceAgentController {

  private final FinancialEvidenceAgentService agentService;

  @Operation(
      summary = "자산 근거 질문",
      description =
          "요청 경로의 자산만 조회한다. 애플리케이션이 질문별 읽기 전용 Tool을 제한하고 LLM은 Tool 호출과 최종 설명만 담당하며 금융 계산과 판정은 서버가 수행한다.")
  @PostMapping("/{assetId}/ask")
  public ResponseEntity<FinancialAgentResponse> ask(
      @CurrentUserId Long userId,
      @PathVariable Long assetId,
      @Valid @RequestBody FinancialAgentRequest request) {
    return ResponseEntity.ok(agentService.ask(userId, assetId, request.question()));
  }
}
