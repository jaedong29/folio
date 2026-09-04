package com.assetdashboard.evidence.evaluation;

import com.assetdashboard.evidence.agent.FinancialAgentProperties;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("local")
@Service
@RequiredArgsConstructor
public class LiveEvaluationBatchQueueService {

  public static final int MAX_LIVE_CASES = 5;
  public static final List<String> SUPPORTED_CASES =
      List.of(
          "fresh-valuation",
          "missing-price",
          "missing-fx",
          "missing-cost-basis",
          "symbol-official-news");

  private final LiveEvaluationBatchJobRepository repository;
  private final FinancialAgentProperties properties;

  @Transactional
  public synchronized LiveEvaluationBatchResponse enqueue(
      Long userId, LiveEvaluationBatchRequest request) {
    if (!Boolean.TRUE.equals(request.confirmLiveCalls())) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "confirmLiveCalls=true일 때만 실제 NIM 평가를 시작합니다.");
    }
    if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
      throw new BusinessException(ErrorCode.AI_AGENT_DISABLED);
    }
    List<String> requested = request.caseIds().isEmpty() ? SUPPORTED_CASES : request.caseIds();
    List<String> distinct = new LinkedHashSet<>(requested).stream().toList();
    if (distinct.size() != requested.size()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "중복 caseId는 실행할 수 없습니다.");
    }
    if (distinct.isEmpty() || distinct.size() > MAX_LIVE_CASES) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "실제 NIM 평가는 한 배치에 1~5건만 실행할 수 있습니다.");
    }
    if (!SUPPORTED_CASES.containsAll(distinct)) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "현재 실제 실행을 지원하지 않는 caseId가 포함되어 있습니다.");
    }
    Optional<LiveEvaluationBatchJob> active =
        repository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            userId, List.of(LiveEvaluationBatchStatus.PENDING, LiveEvaluationBatchStatus.RUNNING));
    if (active.isPresent()) {
      return LiveEvaluationBatchResponse.from(active.get(), List.of(), true);
    }
    LiveEvaluationBatchJob saved = repository.save(LiveEvaluationBatchJob.pending(userId, distinct));
    return LiveEvaluationBatchResponse.from(saved, List.of(), false);
  }
}
