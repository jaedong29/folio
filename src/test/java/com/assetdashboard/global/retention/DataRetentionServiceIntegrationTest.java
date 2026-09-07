package com.assetdashboard.global.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 실제 커밋 뒤에도 보존 경계와 FK 삭제 순서가 유지되는지 확인한다. */
@SpringBootTest
class DataRetentionServiceIntegrationTest {

  @Autowired private DataRetentionService service;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private Long userId;

  @AfterEach
  void cleanUp() {
    if (userId == null) {
      return;
    }
    deleteBatchRows();
    deleteTraceRows();
    jdbcTemplate.update("delete from audit_logs where subject_user_id = ?", userId);
    userRepository.findById(userId).ifPresent(userRepository::delete);
  }

  @Test
  void removesOnlyExpiredRecordsAndKeepsActiveEvaluationBatch() {
    Instant now = Instant.now();
    userId =
        userRepository
            .saveAndFlush(
                User.create(
                    "retention-" + UUID.randomUUID() + "@example.com",
                    "encoded-password",
                    "retention-user"))
            .getId();

    insertAudit(now.minus(366, ChronoUnit.DAYS));
    insertAudit(now.minus(364, ChronoUnit.DAYS));
    insertTrace("expired-trace", now.minus(31, ChronoUnit.DAYS), true);
    insertTrace("retained-trace", now.minus(29, ChronoUnit.DAYS), true);
    insertTrace("batch-linked-trace", now.minus(31, ChronoUnit.DAYS), true);
    insertBatch(
        "expired-batch",
        "COMPLETED",
        now.minus(91, ChronoUnit.DAYS),
        "expired-trace");
    insertBatch(
        "retained-batch",
        "FAILED",
        now.minus(89, ChronoUnit.DAYS),
        "batch-linked-trace");
    insertBatch("active-old-batch", "RUNNING", null, null);

    DataRetentionCleanupResult result = service.evictExpiredRecords();

    assertThat(result).isEqualTo(new DataRetentionCleanupResult(1, 1, 1));
    assertThat(count("audit_logs")).isEqualTo(1);
    assertThat(traceIds()).containsExactly("batch-linked-trace", "retained-trace");
    assertThat(count("ai_agent_spans")).isEqualTo(2);
    assertThat(count("ai_evaluation_results")).isEqualTo(2);
    assertThat(batchIds()).containsExactlyInAnyOrder("retained-batch", "active-old-batch");
    assertThat(count("live_evaluation_batch_cases")).isEqualTo(1);
  }

  private void insertAudit(Instant createdAt) {
    jdbcTemplate.update(
        "insert into audit_logs (subject_user_id, action, created_at) values (?, ?, ?)",
        userId,
        "PASSWORD_CHANGED",
        databaseTimestamp(createdAt));
  }

  private void insertTrace(String traceId, Instant createdAt, boolean withChildren) {
    jdbcTemplate.update(
        """
        insert into ai_agent_runs
          (trace_id, user_id, question_hash, prompt_version, model, status, latency_ms,
           hard_failure, created_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        traceId,
        userId,
        "a".repeat(64),
        "retention-test-v1",
        "test-model",
        "COMPLETED",
        1L,
        false,
        databaseTimestamp(createdAt));
    if (!withChildren) {
      return;
    }
    Long runId =
        jdbcTemplate.queryForObject(
            "select id from ai_agent_runs where trace_id = ?", Long.class, traceId);
    jdbcTemplate.update(
        """
        insert into ai_agent_spans
          (run_id, span_id, step_type, name, status, latency_ms, created_at)
        values (?, ?, ?, ?, ?, ?, ?)
        """,
        runId,
        "root-" + traceId,
        "AGENT",
        "retentionTest",
        "SUCCESS",
        1L,
        databaseTimestamp(createdAt));
    jdbcTemplate.update(
        """
        insert into ai_evaluation_results
          (run_id, case_id, scorer_version, passed, hard_failure, created_at)
        values (?, ?, ?, ?, ?, ?)
        """,
        runId,
        "retention-case",
        "v1",
        true,
        false,
        databaseTimestamp(createdAt));
  }

  private void insertBatch(
      String batchId, String status, Instant completedAt, String caseTraceId) {
    Instant createdAt =
        completedAt == null ? Instant.now().minus(200, ChronoUnit.DAYS) : completedAt;
    jdbcTemplate.update(
        """
        insert into live_evaluation_batches
          (batch_id, user_id, case_ids, status, completed_at, completed_count, passed_count,
           hard_failure_count, total_latency_ms, total_input_tokens, total_output_tokens,
           observed_model_calls, version, created_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        batchId,
        userId,
        "retention-case",
        status,
        completedAt == null ? null : Timestamp.from(completedAt),
        caseTraceId == null ? 0 : 1,
        caseTraceId == null ? 0 : 1,
        0,
        1L,
        1L,
        1L,
        1,
        0L,
        Timestamp.from(createdAt));
    if (caseTraceId == null) {
      return;
    }
    Long jobId =
        jdbcTemplate.queryForObject(
            "select id from live_evaluation_batches where batch_id = ?", Long.class, batchId);
    jdbcTemplate.update(
        """
        insert into live_evaluation_batch_cases
          (batch_job_id, case_id, trace_id, passed, hard_failure, latency_ms, input_tokens,
           output_tokens, model_call_count, created_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        jobId,
        "retention-case",
        caseTraceId,
        true,
        false,
        1L,
        1L,
        1L,
        1,
        Timestamp.from(createdAt));
  }

  private int count(String table) {
    String sql =
        switch (table) {
          case "audit_logs" ->
              "select count(*) from audit_logs where subject_user_id = ?";
          case "ai_agent_spans" ->
              """
              select count(*) from ai_agent_spans
              where run_id in (select id from ai_agent_runs where user_id = ?)
              """;
          case "ai_evaluation_results" ->
              """
              select count(*) from ai_evaluation_results
              where run_id in (select id from ai_agent_runs where user_id = ?)
              """;
          case "live_evaluation_batch_cases" ->
              """
              select count(*) from live_evaluation_batch_cases
              where batch_job_id in
                (select id from live_evaluation_batches where user_id = ?)
              """;
          default -> throw new IllegalArgumentException("지원하지 않는 테스트 테이블: " + table);
        };
    return jdbcTemplate.queryForObject(sql, Integer.class, userId);
  }

  private List<String> traceIds() {
    return jdbcTemplate.queryForList(
        "select trace_id from ai_agent_runs where user_id = ? order by trace_id",
        String.class,
        userId);
  }

  private List<String> batchIds() {
    return jdbcTemplate.queryForList(
        "select batch_id from live_evaluation_batches where user_id = ? order by batch_id",
        String.class,
        userId);
  }

  private void deleteTraceRows() {
    List<Long> ids =
        jdbcTemplate.queryForList(
            "select id from ai_agent_runs where user_id = ?", Long.class, userId);
    for (Long id : ids) {
      jdbcTemplate.update("delete from ai_evaluation_results where run_id = ?", id);
      jdbcTemplate.update("delete from ai_agent_spans where run_id = ?", id);
    }
    jdbcTemplate.update("delete from ai_agent_runs where user_id = ?", userId);
  }

  private void deleteBatchRows() {
    List<Long> ids =
        jdbcTemplate.queryForList(
            "select id from live_evaluation_batches where user_id = ?", Long.class, userId);
    for (Long id : ids) {
      jdbcTemplate.update("delete from live_evaluation_batch_cases where batch_job_id = ?", id);
    }
    jdbcTemplate.update("delete from live_evaluation_batches where user_id = ?", userId);
  }

  private LocalDateTime databaseTimestamp(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
  }
}
