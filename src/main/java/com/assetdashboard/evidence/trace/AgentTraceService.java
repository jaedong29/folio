package com.assetdashboard.evidence.trace;

import com.assetdashboard.evidence.evaluation.FinancialEvidenceEvaluationResult;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Agent 실행 트리와 평가 결과를 민감 원문 없이 저장하고 사용자별로 조회한다. */
@Service
@RequiredArgsConstructor
public class AgentTraceService {

  private static final int MAX_LIST_LIMIT = 100;

  private final AgentTraceRunRepository runRepository;
  private final AgentTraceSpanRepository spanRepository;
  private final AgentEvaluationRecordRepository evaluationRepository;

  @Transactional
  public AgentTraceResponse record(
      Long userId,
      String caseId,
      String question,
      AgentRunResult result,
      FinancialEvidenceEvaluationResult evaluation) {
    validateRecord(caseId, question, result, evaluation);
    if (runRepository.existsByTraceId(result.traceId())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 기록된 traceId입니다.");
    }

    AgentTraceRun run =
        runRepository.save(
            AgentTraceRun.create(
                userId,
                trimToNull(caseId),
                sha256(question),
                result.finalAnswer() == null ? null : sha256(result.finalAnswer()),
                result,
                evaluation != null && evaluation.hardFailure()));
    List<AgentTraceStep> recordedSteps = new ArrayList<>(result.steps());
    if (evaluation != null) {
      String rootSpanId =
          result.steps().stream()
              .filter(step -> step.parentSpanId() == null)
              .findFirst()
              .orElseThrow()
              .spanId();
      recordedSteps.add(evaluationStep(rootSpanId, evaluation));
    }
    validateTree(recordedSteps);
    List<AgentTraceSpan> spans =
        recordedSteps.stream().map(step -> AgentTraceSpan.create(run.getId(), step)).toList();
    spanRepository.saveAll(spans);

    AgentEvaluationRecord savedEvaluation =
        evaluation == null
            ? null
            : evaluationRepository.save(AgentEvaluationRecord.create(run.getId(), evaluation));
    return toResponse(run, spans, savedEvaluation);
  }

  @Transactional(readOnly = true)
  public List<AgentTraceSummary> getRecent(Long userId, int limit) {
    if (limit < 1 || limit > MAX_LIST_LIMIT) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "limit은 1 이상 100 이하여야 합니다.");
    }
    return runRepository
        .findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
        .stream()
        .map(AgentTraceSummary::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public AgentTraceResponse getTrace(Long userId, String traceId) {
    AgentTraceRun run =
        runRepository
            .findByTraceIdAndUserId(traceId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_TRACE_NOT_FOUND));
    return toResponse(
        run,
        spanRepository.findAllByRunIdOrderByIdAsc(run.getId()),
        evaluationRepository.findByRunId(run.getId()).orElse(null));
  }

  private void validateRecord(
      String caseId,
      String question,
      AgentRunResult result,
      FinancialEvidenceEvaluationResult evaluation) {
    if (question == null || question.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "평가 질문이 필요합니다.");
    }
    try {
      UUID.fromString(result.traceId());
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "traceId는 UUID 형식이어야 합니다.");
    }
    if (evaluation != null) {
      if (!result.traceId().equals(evaluation.traceId())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT, "실행 결과와 평가 결과의 traceId가 다릅니다.");
      }
      if (caseId == null || !caseId.equals(evaluation.caseId())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT, "실행 결과와 평가 결과의 caseId가 다릅니다.");
      }
    }
    validateTree(result.steps());
  }

  private void validateTree(List<AgentTraceStep> steps) {
    if (steps.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "Trace 단계가 하나 이상 필요합니다.");
    }
    Map<String, AgentTraceStep> byId = new HashMap<>();
    for (AgentTraceStep step : steps) {
      if (step.name().length() > 120 || !step.name().matches("[A-Za-z0-9._:-]+")) {
        throw new BusinessException(
            ErrorCode.INVALID_INPUT, "Trace 단계 이름은 원문이 아닌 안전한 연산 이름이어야 합니다.");
      }
      if (step.errorCode() != null
          && (step.errorCode().length() > 80
              || !step.errorCode().matches("[A-Z0-9_:-]+"))) {
        throw new BusinessException(
            ErrorCode.INVALID_INPUT, "Trace에는 오류 메시지가 아닌 안전한 오류 코드만 저장할 수 있습니다.");
      }
      if (byId.put(step.spanId(), step) != null) {
        throw new BusinessException(ErrorCode.INVALID_INPUT, "중복된 spanId입니다: " + step.spanId());
      }
      validateSafeReferences(step.referenceIds());
    }

    List<AgentTraceStep> roots =
        steps.stream().filter(step -> step.parentSpanId() == null).toList();
    if (roots.size() != 1 || roots.get(0).type() != AgentTraceStepType.AGENT) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "AGENT 유형의 루트 Span이 정확히 하나 필요합니다.");
    }
    for (AgentTraceStep step : steps) {
      if (step.parentSpanId() != null && !byId.containsKey(step.parentSpanId())) {
        throw new BusinessException(
            ErrorCode.INVALID_INPUT, "부모 Span을 찾을 수 없습니다: " + step.parentSpanId());
      }
      assertNoCycle(step, byId);
    }
  }

  private void assertNoCycle(AgentTraceStep start, Map<String, AgentTraceStep> byId) {
    Set<String> visited = new HashSet<>();
    AgentTraceStep current = start;
    while (current != null) {
      if (!visited.add(current.spanId())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT, "Trace Span에 순환 참조가 있습니다.");
      }
      current =
          current.parentSpanId() == null ? null : byId.get(current.parentSpanId());
    }
  }

  private void validateSafeReferences(List<String> referenceIds) {
    for (String referenceId : referenceIds) {
      if (referenceId == null
          || referenceId.length() > 100
          || !referenceId.matches("[A-Za-z0-9._:-]+")) {
        throw new BusinessException(
            ErrorCode.INVALID_INPUT, "Trace에는 원문이 아닌 안전한 참조 id만 저장할 수 있습니다.");
      }
    }
  }

  private AgentTraceStep evaluationStep(
      String rootSpanId, FinancialEvidenceEvaluationResult evaluation) {
    AgentTraceStepStatus status =
        evaluation.passed()
            ? AgentTraceStepStatus.SUCCESS
            : evaluation.hardFailure()
                ? AgentTraceStepStatus.BLOCKED
                : AgentTraceStepStatus.ERROR;
    return new AgentTraceStep(
        "evaluation-" + UUID.randomUUID(),
        rootSpanId,
        AgentTraceStepType.EVALUATION,
        evaluation.scorerVersion(),
        status,
        0,
        evaluation.failureCodes().isEmpty()
            ? null
            : evaluation.failureCodes().stream()
                .map(Enum::name)
                .sorted()
                .findFirst()
                .orElse(null),
        List.of("case:" + evaluation.caseId()));
  }

  private AgentTraceResponse toResponse(
      AgentTraceRun run,
      List<AgentTraceSpan> spans,
      AgentEvaluationRecord evaluation) {
    return new AgentTraceResponse(
        run.getTraceId(),
        run.getCaseId(),
        run.getPromptVersion(),
        run.getModel(),
        run.getStatus(),
        run.getConclusion(),
        run.getLatencyMs(),
        run.getInputTokens(),
        run.getOutputTokens(),
        run.isHardFailure(),
        false,
        false,
        run.getCreatedAt(),
        buildTree(spans),
        toEvaluationSummary(evaluation));
  }

  private List<AgentTraceResponse.AgentTraceNode> buildTree(List<AgentTraceSpan> spans) {
    Map<String, MutableNode> nodes = new LinkedHashMap<>();
    spans.forEach(span -> nodes.put(span.getSpanId(), new MutableNode(span)));
    List<MutableNode> roots = new ArrayList<>();
    for (MutableNode node : nodes.values()) {
      String parentSpanId = node.span.getParentSpanId();
      if (parentSpanId == null) {
        roots.add(node);
      } else {
        MutableNode parent = nodes.get(parentSpanId);
        if (parent != null) {
          parent.children.add(node);
        }
      }
    }
    return roots.stream().map(MutableNode::toResponse).toList();
  }

  private AgentTraceResponse.EvaluationSummary toEvaluationSummary(
      AgentEvaluationRecord evaluation) {
    if (evaluation == null) {
      return null;
    }
    return new AgentTraceResponse.EvaluationSummary(
        evaluation.getCaseId(),
        evaluation.getScorerVersion(),
        evaluation.isPassed(),
        evaluation.isHardFailure(),
        split(evaluation.getFailureCodes(), ","),
        split(evaluation.getFailureDetails(), "\\R"));
  }

  private List<String> split(String value, String delimiterRegex) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(delimiterRegex)).filter(part -> !part.isBlank()).toList();
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM이 SHA-256을 지원하지 않습니다.", e);
    }
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static final class MutableNode {
    private final AgentTraceSpan span;
    private final List<MutableNode> children = new ArrayList<>();

    private MutableNode(AgentTraceSpan span) {
      this.span = span;
    }

    private AgentTraceResponse.AgentTraceNode toResponse() {
      List<String> references =
          span.getReferenceIds() == null
              ? List.of()
              : Arrays.stream(span.getReferenceIds().split(","))
                  .filter(value -> !value.isBlank())
                  .toList();
      return new AgentTraceResponse.AgentTraceNode(
          span.getSpanId(),
          span.getType(),
          span.getName(),
          span.getStatus(),
          span.getLatencyMs(),
          span.getErrorCode(),
          references,
          children.stream()
              .sorted(Comparator.comparing(node -> node.span.getId()))
              .map(MutableNode::toResponse)
              .toList());
    }
  }
}
