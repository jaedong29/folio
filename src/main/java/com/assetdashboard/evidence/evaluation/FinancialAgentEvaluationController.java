package com.assetdashboard.evidence.evaluation;

import com.assetdashboard.evidence.agent.FinancialEvidenceAgentService;
import com.assetdashboard.evidence.trace.AgentTraceResponse;
import com.assetdashboard.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 실제 NIM 실행을 골든셋으로 채점하는 local 전용 API. */
@Profile("local")
@Tag(name = "Financial Agent Evaluation", description = "local 합성 fixture와 실제 NIM 평가")
@RestController
@RequestMapping("/api/ai/evaluations")
@RequiredArgsConstructor
public class FinancialAgentEvaluationController {

  private final FinancialAgentEvaluationFixtureService fixtureService;
  private final FinancialEvidenceAgentService agentService;
  private final LiveEvaluationBatchQueueService batchQueueService;
  private final LiveEvaluationBatchLifecycleService batchLifecycleService;

  @Operation(
      summary = "골든 케이스 합성 자산 생성",
      description =
          "지원: fresh-valuation, missing-price, missing-fx, missing-cost-basis, symbol-official-news, "
              + "stale-price, stale-fx, transaction-evidence, price-direction, no-symbol-evidence, "
              + "user-asserted-official, verified-dart, verified-kind, verified-sec, prompt-injection, "
              + "cross-user-document")
  @PostMapping("/fixtures/{caseId}")
  public ResponseEntity<EvaluationFixtureResponse> createFixture(
      @CurrentUserId Long userId, @PathVariable String caseId) {
    return ResponseEntity.ok(fixtureService.create(userId, caseId));
  }

  @Operation(
      summary = "단일 Asset Evidence 골든 케이스 실행",
      description = "fixture 생성 응답의 caseId와 assetId를 그대로 사용해 실제 NIM 실행을 채점한다.")
  @PostMapping("/cases/{caseId}/assets/{assetId}/run")
  public ResponseEntity<AgentTraceResponse> runCase(
      @CurrentUserId Long userId,
      @PathVariable String caseId,
      @PathVariable Long assetId) {
    return ResponseEntity.ok(agentService.evaluate(userId, assetId, caseId));
  }

  @Operation(
      summary = "최대 5건 실제 NIM 평가 배치 시작",
      description = "confirmLiveCalls=true가 필요하며 비동기로 실행한다. 빈 caseIds는 기본 5건(계산 4건 + "
          + "symbol-official-news)을 뜻하며, 나머지 4건(stale-price, stale-fx, transaction-evidence, "
          + "price-direction)은 caseIds에 명시해야 한다. 한 배치는 caseIds를 몇 개 지정하든 최대 5건까지다.")
  @PostMapping("/live-runs")
  public ResponseEntity<LiveEvaluationBatchResponse> startLiveRun(
      @CurrentUserId Long userId, @Valid @RequestBody LiveEvaluationBatchRequest request) {
    return ResponseEntity.accepted().body(batchQueueService.enqueue(userId, request));
  }

  @Operation(summary = "실제 NIM 평가 배치 상태·집계 조회")
  @GetMapping("/live-runs/{batchId}")
  public ResponseEntity<LiveEvaluationBatchResponse> getLiveRun(
      @CurrentUserId Long userId, @PathVariable String batchId) {
    return ResponseEntity.ok(batchLifecycleService.get(userId, batchId));
  }
}
