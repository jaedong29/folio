package com.assetdashboard.news;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsSummaryWorkerTest {

  @Mock private NewsSummaryLifecycleService lifecycleService;
  @Mock private NewsSummaryModelClient modelClient;
  @Mock private NewsSummaryGuardrail guardrail;
  private NewsSummaryWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new NewsSummaryWorker(
            new NewsProperties(true, 1000, 1000, 360, 10, 1000, true, 1200, 6000),
            lifecycleService,
            modelClient,
            guardrail);
  }

  @Test
  void completesSafeSummary() {
    ClaimedNewsSummary claimed =
        new ClaimedNewsSummary(1L, "hash", "title", "publisher", "content");
    NewsSummaryDraft draft =
        new NewsSummaryDraft("요약입니다.", "기술적 의미입니다.", "model", 10, 20, 5);
    when(modelClient.isEnabled()).thenReturn(true);
    when(lifecycleService.claimNext()).thenReturn(Optional.of(claimed));
    when(modelClient.summarize(claimed)).thenReturn(draft);
    when(guardrail.validate(claimed, draft)).thenReturn(Optional.empty());

    worker.processNext();

    verify(lifecycleService).complete(claimed, draft);
    verify(lifecycleService, never()).fail(claimed, "SUMMARY_UNSAFE_CLAIM");
  }

  @Test
  void storesOnlySafeFailureCodeWhenGuardrailBlocks() {
    ClaimedNewsSummary claimed =
        new ClaimedNewsSummary(1L, "hash", "title", "publisher", "content");
    NewsSummaryDraft draft =
        new NewsSummaryDraft("요약입니다.", "가격 상승 예상", "model", 10, 20, 5);
    when(modelClient.isEnabled()).thenReturn(true);
    when(lifecycleService.claimNext()).thenReturn(Optional.of(claimed));
    when(modelClient.summarize(claimed)).thenReturn(draft);
    when(guardrail.validate(claimed, draft)).thenReturn(Optional.of("SUMMARY_UNSAFE_CLAIM"));

    worker.processNext();

    verify(lifecycleService).fail(claimed, "SUMMARY_UNSAFE_CLAIM");
    verify(lifecycleService, never()).complete(claimed, draft);
  }

  @Test
  void doesNotClaimWorkWhenSummaryClientIsDisabled() {
    when(modelClient.isEnabled()).thenReturn(false);

    worker.processNext();

    verify(lifecycleService, never()).claimNext();
  }
}
