package com.assetdashboard.evidence.evaluation;

import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("local")
@Service
@RequiredArgsConstructor
public class LiveEvaluationBatchLifecycleService {

  private final LiveEvaluationBatchJobRepository jobRepository;
  private final LiveEvaluationBatchCaseRepository caseRepository;
  private final Clock clock;

  @Transactional
  public Optional<ClaimedLiveEvaluationBatch> claimNext() {
    return jobRepository
        .findNextForUpdate(LiveEvaluationBatchStatus.PENDING, PageRequest.of(0, 1))
        .stream()
        .findFirst()
        .map(
            job -> {
              job.start(Instant.now(clock));
              return new ClaimedLiveEvaluationBatch(
                  job.getId(), job.getBatchId(), job.getUserId(), job.requestedCaseIds());
            });
  }

  @Transactional(readOnly = true)
  public boolean hasResult(Long batchJobId, String caseId) {
    return caseRepository.existsByBatchJobIdAndCaseId(batchJobId, caseId);
  }

  @Transactional
  public void record(LiveEvaluationBatchCaseResult result) {
    if (caseRepository.existsByBatchJobIdAndCaseId(result.getBatchJobId(), result.getCaseId())) {
      return;
    }
    LiveEvaluationBatchJob job = requireJob(result.getBatchJobId());
    caseRepository.save(result);
    job.addResult(result);
  }

  @Transactional
  public void complete(Long id) {
    requireJob(id).complete(Instant.now(clock));
  }

  @Transactional
  public void fail(Long id, String safeErrorCode) {
    requireJob(id).fail(Instant.now(clock), safeErrorCode);
  }

  @Transactional
  public void recoverInterrupted() {
    jobRepository
        .findAllByStatus(LiveEvaluationBatchStatus.RUNNING)
        .forEach(LiveEvaluationBatchJob::retryInterrupted);
  }

  @Transactional(readOnly = true)
  public LiveEvaluationBatchResponse get(Long userId, String batchId) {
    LiveEvaluationBatchJob job =
        jobRepository
            .findByBatchIdAndUserId(batchId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.EVALUATION_BATCH_NOT_FOUND));
    return LiveEvaluationBatchResponse.from(
        job, caseRepository.findAllByBatchJobIdOrderByIdAsc(job.getId()), false);
  }

  private LiveEvaluationBatchJob requireJob(Long id) {
    return jobRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.EVALUATION_BATCH_NOT_FOUND));
  }
}
