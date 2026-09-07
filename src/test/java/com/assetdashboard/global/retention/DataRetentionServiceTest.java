package com.assetdashboard.global.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");
  private static final PageRequest FIRST_BATCH = PageRequest.of(0, 500);

  @Mock private AuditLogRepository auditLogRepository;
  @Mock private AgentTraceRunRepository agentTraceRunRepository;
  @Mock private AgentTraceSpanRepository agentTraceSpanRepository;
  @Mock private AgentEvaluationRecordRepository agentEvaluationRecordRepository;
  @Mock private LiveEvaluationBatchJobRepository evaluationBatchJobRepository;
  @Mock private LiveEvaluationBatchCaseRepository evaluationBatchCaseRepository;

  private DataRetentionService service;

  @BeforeEach
  void setUp() {
    service =
        new DataRetentionService(
            auditLogRepository,
            agentTraceRunRepository,
            agentTraceSpanRepository,
            agentEvaluationRecordRepository,
            evaluationBatchJobRepository,
            evaluationBatchCaseRepository,
            new DataRetentionProperties(365, 30, 90, 86_400_000),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void deletesExpiredRootsInBoundedBatchesAndChildrenFirst() {
    LocalDateTime auditCutoff = databaseTime(NOW.minus(365, ChronoUnit.DAYS));
    LocalDateTime traceCutoff = databaseTime(NOW.minus(30, ChronoUnit.DAYS));
    Instant batchCutoff = NOW.minus(90, ChronoUnit.DAYS);
    List<Long> auditIds = List.of(1L, 2L);
    List<Long> traceIds = List.of(11L, 12L);
    List<Long> batchIds = List.of(21L);

    when(auditLogRepository.findIdsByCreatedAtBefore(auditCutoff, FIRST_BATCH))
        .thenReturn(auditIds)
        .thenReturn(List.of());
    when(agentTraceRunRepository.findIdsByCreatedAtBefore(traceCutoff, FIRST_BATCH))
        .thenReturn(traceIds)
        .thenReturn(List.of());
    when(evaluationBatchJobRepository.findIdsByStatusInAndCompletedAtBefore(
            List.of(LiveEvaluationBatchStatus.COMPLETED, LiveEvaluationBatchStatus.FAILED),
            batchCutoff,
            FIRST_BATCH))
        .thenReturn(batchIds)
        .thenReturn(List.of());

    DataRetentionCleanupResult result = service.evictExpiredRecords();

    assertThat(result).isEqualTo(new DataRetentionCleanupResult(2, 2, 1));
    verify(auditLogRepository).deleteAllByIdInBatch(auditIds);

    InOrder traceDeletion =
        inOrder(
            agentEvaluationRecordRepository,
            agentTraceSpanRepository,
            agentTraceRunRepository);
    traceDeletion.verify(agentEvaluationRecordRepository).deleteAllByRunIdIn(traceIds);
    traceDeletion.verify(agentTraceSpanRepository).deleteAllByRunIdIn(traceIds);
    traceDeletion.verify(agentTraceRunRepository).deleteAllByIdInBatch(traceIds);

    InOrder batchDeletion =
        inOrder(evaluationBatchCaseRepository, evaluationBatchJobRepository);
    batchDeletion.verify(evaluationBatchCaseRepository).deleteAllByBatchJobIdIn(batchIds);
    batchDeletion.verify(evaluationBatchJobRepository).deleteAllByIdInBatch(batchIds);

    verify(auditLogRepository, times(2))
        .findIdsByCreatedAtBefore(auditCutoff, FIRST_BATCH);
    verify(agentTraceRunRepository, times(2))
        .findIdsByCreatedAtBefore(traceCutoff, FIRST_BATCH);
    verify(evaluationBatchJobRepository, times(2))
        .findIdsByStatusInAndCompletedAtBefore(
            List.of(LiveEvaluationBatchStatus.COMPLETED, LiveEvaluationBatchStatus.FAILED),
            batchCutoff,
            FIRST_BATCH);
  }

  private LocalDateTime databaseTime(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
  }
}
