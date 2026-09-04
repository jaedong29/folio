package com.assetdashboard.domain.user.service;

import com.assetdashboard.dashboard.snapshot.PortfolioSnapshotRepository;
import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.repository.AssetRepository;
import com.assetdashboard.domain.transaction.repository.IdempotencyKeyRepository;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.domain.user.dto.ChangePasswordRequest;
import com.assetdashboard.domain.user.dto.DeleteAccountRequest;
import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.evidence.document.EvidenceDocumentRepository;
import com.assetdashboard.evidence.evaluation.LiveEvaluationBatchCaseRepository;
import com.assetdashboard.evidence.evaluation.LiveEvaluationBatchJob;
import com.assetdashboard.evidence.evaluation.LiveEvaluationBatchJobRepository;
import com.assetdashboard.evidence.trace.AgentEvaluationRecordRepository;
import com.assetdashboard.evidence.trace.AgentTraceRunRepository;
import com.assetdashboard.evidence.trace.AgentTraceSpanRepository;
import com.assetdashboard.global.audit.AuditAction;
import com.assetdashboard.global.audit.AuditLogService;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.global.security.RefreshTokenRepository;
import com.assetdashboard.global.security.RefreshTokenService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자의 비밀번호 변경과 계정 데이터 삭제를 담당한다. */
@Service
@RequiredArgsConstructor
public class UserAccountService {

  private final UserRepository userRepository;
  private final AssetRepository assetRepository;
  private final TransactionRepository transactionRepository;
  private final PortfolioSnapshotRepository portfolioSnapshotRepository;
  private final EvidenceDocumentRepository evidenceDocumentRepository;
  private final AgentTraceRunRepository agentTraceRunRepository;
  private final AgentTraceSpanRepository agentTraceSpanRepository;
  private final AgentEvaluationRecordRepository agentEvaluationRecordRepository;
  private final LiveEvaluationBatchJobRepository liveEvaluationBatchJobRepository;
  private final LiveEvaluationBatchCaseRepository liveEvaluationBatchCaseRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenService refreshTokenService;
  private final IdempotencyKeyRepository idempotencyKeyRepository;
  private final AuditLogService auditLogService;
  private final PasswordEncoder passwordEncoder;

  /** 현재 비밀번호를 확인하고 새 비밀번호를 저장한다.
   *
   * @param userId 인증된 사용자 id
   * @param request 현재 비밀번호와 새 비밀번호
   */
  @Transactional
  public void changePassword(Long userId, ChangePasswordRequest request) {
    User user = getUser(userId);
    verifyPassword(user, request.currentPassword());
    if (request.currentPassword().equals(request.newPassword())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
    }
    user.changePassword(passwordEncoder.encode(request.newPassword()));
    // 비밀번호가 새어나갔을 가능성에 대비해 다른 기기의 세션도 모두 끊는다.
    refreshTokenService.revokeAllForUser(userId);
    auditLogService.record(userId, AuditAction.PASSWORD_CHANGED);
  }

  /** 비밀번호와 확인 문구를 검증한 뒤 계정의 모든 데이터를 영구 삭제한다.
   *
   * @param userId 인증된 사용자 id
   * @param request 현재 비밀번호와 DELETE 확인 문구
   */
  @Transactional
  public void deleteAccount(Long userId, DeleteAccountRequest request) {
    User user = getUser(userId);
    verifyPassword(user, request.password());
    if (!"DELETE".equals(request.confirmation())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "탈퇴하려면 DELETE를 입력해야 합니다.");
    }

    List<Long> assetIds =
        assetRepository.findAllByUserId(userId).stream().map(Asset::getId).toList();
    List<Long> traceRunIds = agentTraceRunRepository.findIdsByUserId(userId);
    List<Long> evaluationBatchIds =
        liveEvaluationBatchJobRepository.findAllByUserId(userId).stream()
            .map(LiveEvaluationBatchJob::getId)
            .toList();
    if (!evaluationBatchIds.isEmpty()) {
      liveEvaluationBatchCaseRepository.deleteAllByBatchJobIdIn(evaluationBatchIds);
      liveEvaluationBatchJobRepository.deleteAllByUserId(userId);
    }
    if (!traceRunIds.isEmpty()) {
      agentEvaluationRecordRepository.deleteAllByRunIdIn(traceRunIds);
      agentTraceSpanRepository.deleteAllByRunIdIn(traceRunIds);
      agentTraceRunRepository.deleteAllByUserId(userId);
    }
    evidenceDocumentRepository.deleteAllByUserId(userId);
    refreshTokenRepository.deleteAllByUserId(userId);
    idempotencyKeyRepository.deleteAllByUserId(userId);
    if (!assetIds.isEmpty()) {
      transactionRepository.deleteAllByAssetIdIn(assetIds);
      assetRepository.deleteAllByIdInBatch(assetIds);
    }
    portfolioSnapshotRepository.deleteAllByUserId(userId);
    userRepository.delete(user);
    // users FK를 두지 않아 탈퇴 트랜잭션이 커밋된 뒤에도 최소 감사 기록은 보존한다.
    auditLogService.record(userId, AuditAction.ACCOUNT_DELETED);
  }

  private User getUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
  }

  private void verifyPassword(User user, String rawPassword) {
    if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
  }
}
