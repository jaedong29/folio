package com.assetdashboard.evidence.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetdashboard.evidence.agent.FinancialAgentProperties;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveEvaluationBatchQueueServiceTest {

  private final LiveEvaluationBatchJobRepository repository =
      mock(LiveEvaluationBatchJobRepository.class);
  private LiveEvaluationBatchQueueService service;

  @BeforeEach
  void setUp() {
    FinancialAgentProperties properties =
        new FinancialAgentProperties(
            true, "https://nim.test/v1", "test-key", "model", 1, 1, false, 0, 0);
    service = new LiveEvaluationBatchQueueService(repository, properties);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void emptyCaseListUsesTheFiveSupportedCases() {
    LiveEvaluationBatchResponse response =
        service.enqueue(7L, new LiveEvaluationBatchRequest(true, List.of()));

    assertThat(response.mode()).isEqualTo("LIVE_NIM");
    assertThat(response.status()).isEqualTo(LiveEvaluationBatchStatus.PENDING);
    assertThat(response.caseIds()).containsExactlyElementsOf(LiveEvaluationBatchQueueService.SUPPORTED_CASES);
    assertThat(response.requestedCount()).isEqualTo(5);
    assertThat(response.rawQuestionStored()).isFalse();
    assertThat(response.rawAnswerStored()).isFalse();
    assertThat(response.reused()).isFalse();
    assertThat(response.maximumProviderCalls()).isEqualTo(10);
  }

  @Test
  void requiresExplicitLiveCallConfirmation() {
    assertThatThrownBy(
            () -> service.enqueue(7L, new LiveEvaluationBatchRequest(false, List.of("missing-fx"))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
  }

  @Test
  void rejectsDuplicateOrUnsupportedCases() {
    assertThatThrownBy(
            () ->
                service.enqueue(
                    7L,
                    new LiveEvaluationBatchRequest(
                        true, List.of("missing-fx", "missing-fx"))))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(
            () ->
                service.enqueue(
                    7L, new LiveEvaluationBatchRequest(true, List.of("unsupported-case"))))
        .isInstanceOf(BusinessException.class);
  }
}
