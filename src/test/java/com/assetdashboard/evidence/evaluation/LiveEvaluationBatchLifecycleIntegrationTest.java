package com.assetdashboard.evidence.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Transactional
class LiveEvaluationBatchLifecycleIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private LiveEvaluationBatchJobRepository jobRepository;
  @Autowired private LiveEvaluationBatchLifecycleService lifecycleService;

  @Test
  void aggregatesSafeMetricsAndHidesAnotherUsersBatch() {
    User owner = userRepository.save(User.create("batch-owner@example.com", "encoded", "owner"));
    User other = userRepository.save(User.create("batch-other@example.com", "encoded", "other"));
    LiveEvaluationBatchJob job =
        LiveEvaluationBatchJob.pending(owner.getId(), List.of("missing-fx"));
    job.start(Instant.parse("2026-09-05T00:00:00Z"));
    job = jobRepository.saveAndFlush(job);
    String batchId = job.getBatchId();

    lifecycleService.record(
        LiveEvaluationBatchCaseResult.failed(
            job.getId(), "missing-fx", "AI_PROVIDER_UNAVAILABLE", 321));
    lifecycleService.complete(job.getId());

    LiveEvaluationBatchResponse response = lifecycleService.get(owner.getId(), batchId);
    assertThat(response.status()).isEqualTo(LiveEvaluationBatchStatus.COMPLETED);
    assertThat(response.completedCount()).isEqualTo(1);
    assertThat(response.hardFailureCount()).isEqualTo(1);
    assertThat(response.averageLatencyMs()).isEqualTo(321);
    assertThat(response.p95LatencyMs()).isEqualTo(321);
    assertThat(response.results().get(0).errorCode()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
    assertThat(response.rawQuestionStored()).isFalse();
    assertThat(response.rawAnswerStored()).isFalse();

    assertThatThrownBy(() -> lifecycleService.get(other.getId(), batchId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.EVALUATION_BATCH_NOT_FOUND));
  }
}
