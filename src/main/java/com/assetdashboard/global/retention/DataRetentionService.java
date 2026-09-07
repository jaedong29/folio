package com.assetdashboard.global.retention;

import com.assetdashboard.evidence.evaluation.LiveEvaluationBatchCaseRepository;
import com.assetdashboard.evidence.evaluation.LiveEvaluationBatchJobRepository;
import com.assetdashboard.evidence.evaluation.LiveEvaluationBatchStatus;
import com.assetdashboard.evidence.trace.AgentEvaluationRecordRepository;
import com.assetdashboard.evidence.trace.AgentTraceRunRepository;
import com.assetdashboard.evidence.trace.AgentTraceSpanRepository;
import com.assetdashboard.global.audit.AuditLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 보존 기간을 넘긴 감사·Agent Trace·평가 배치 기록을 관계 순서에 맞춰 정리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionService {

  private static final int DELETE_BATCH_SIZE = 500;
  private static final Pageable FIRST_DELETE_BATCH = PageRequest.of(0, DELETE_BATCH_SIZE);
  private static final List<LiveEvaluationBatchStatus> TERMINAL_BATCH_STATUSES =
      List.of(LiveEvaluationBatchStatus.COMPLETED, LiveEvaluationBatchStatus.FAILED);

  private final AuditLogRepository auditLogRepository;
  private final AgentTraceRunRepository agentTraceRunRepository;
  private final AgentTraceSpanRepository agentTraceSpanRepository;
  private final AgentEvaluationRecordRepository agentEvaluationRecordRepository;
  private final LiveEvaluationBatchJobRepository evaluationBatchJobRepository;
  private final LiveEvaluationBatchCaseRepository evaluationBatchCaseRepository;
  private final DataRetentionProperties properties;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${app.retention.cleanup-delay-millis:86400000}")
  @Transactional
  public DataRetentionCleanupResult evictExpiredRecords() {
    Instant now = Instant.now(clock);
    LocalDateTime auditCutoff =
        databaseLocalDateTime(now.minus(properties.auditLogDays(), ChronoUnit.DAYS));
    LocalDateTime traceCutoff =
        databaseLocalDateTime(now.minus(properties.agentTraceDays(), ChronoUnit.DAYS));
    Instant batchCutoff = now.minus(properties.evaluationBatchDays(), ChronoUnit.DAYS);

    int auditLogCount = deleteExpiredAuditLogs(auditCutoff);
    int batchJobCount = deleteExpiredEvaluationBatches(batchCutoff);
    // 보존 중인 평가 배치의 case가 가리키는 Trace는 repository 쿼리에서 제외한다. 만료 배치를 먼저
    // 삭제해야 그 배치만 참조하던 오래된 Trace도 같은 실행에서 정리할 수 있다.
    int traceRunCount = deleteExpiredAgentTraces(traceCutoff);

    DataRetentionCleanupResult result =
        new DataRetentionCleanupResult(auditLogCount, traceRunCount, batchJobCount);
    if (auditLogCount > 0 || traceRunCount > 0 || batchJobCount > 0) {
      log.info(
          "Expired retained records deleted: auditLogs={}, agentTraces={}, evaluationBatches={}",
          result.auditLogCount(),
          result.agentTraceCount(),
          result.evaluationBatchCount());
    }
    return result;
  }

  private int deleteExpiredAuditLogs(LocalDateTime cutoff) {
    int deleted = 0;
    List<Long> ids;
    while (!(ids = auditLogRepository.findIdsByCreatedAtBefore(cutoff, FIRST_DELETE_BATCH))
        .isEmpty()) {
      auditLogRepository.deleteAllByIdInBatch(ids);
      deleted += ids.size();
    }
    return deleted;
  }

  private int deleteExpiredAgentTraces(LocalDateTime cutoff) {
    int deleted = 0;
    List<Long> ids;
    while (!(ids = agentTraceRunRepository.findIdsByCreatedAtBefore(cutoff, FIRST_DELETE_BATCH))
        .isEmpty()) {
      agentEvaluationRecordRepository.deleteAllByRunIdIn(ids);
      agentTraceSpanRepository.deleteAllByRunIdIn(ids);
      agentTraceRunRepository.deleteAllByIdInBatch(ids);
      deleted += ids.size();
    }
    return deleted;
  }

  private int deleteExpiredEvaluationBatches(Instant cutoff) {
    int deleted = 0;
    List<Long> ids;
    while (!(ids =
            evaluationBatchJobRepository.findIdsByStatusInAndCompletedAtBefore(
                TERMINAL_BATCH_STATUSES, cutoff, FIRST_DELETE_BATCH))
        .isEmpty()) {
      evaluationBatchCaseRepository.deleteAllByBatchJobIdIn(ids);
      evaluationBatchJobRepository.deleteAllByIdInBatch(ids);
      deleted += ids.size();
    }
    return deleted;
  }

  private LocalDateTime databaseLocalDateTime(Instant instant) {
    // BaseCreatedEntity의 LocalDateTime은 Spring Data Auditing이 JVM 기본 시간대로 생성한다.
    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
  }
}
