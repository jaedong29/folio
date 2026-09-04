package com.assetdashboard.evidence.trace;

import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증된 사용자가 자신의 Agent 실행·평가 이력을 읽기 전용으로 조회하는 API. */
@Tag(name = "Agent Trace", description = "민감 원문을 제외한 Agent 실행 트리와 평가 결과")
@RestController
@RequestMapping("/api/ai/traces")
@RequiredArgsConstructor
public class AgentTraceController {

  private final AgentTraceService agentTraceService;

  @Operation(summary = "최근 Agent Trace 목록")
  @GetMapping
  public ResponseEntity<List<AgentTraceSummary>> getRecent(
      @CurrentUserId Long userId,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(agentTraceService.getRecent(userId, limit));
  }

  @Operation(summary = "Agent Trace 트리 단건 조회")
  @GetMapping("/{traceId}")
  public ResponseEntity<AgentTraceResponse> getTrace(
      @CurrentUserId Long userId, @PathVariable String traceId) {
    return ResponseEntity.ok(agentTraceService.getTrace(userId, traceId));
  }
}
