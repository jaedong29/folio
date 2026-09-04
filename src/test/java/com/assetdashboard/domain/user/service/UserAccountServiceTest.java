package com.assetdashboard.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.assetdashboard.dashboard.snapshot.PortfolioSnapshotRepository;
import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.repository.AssetRepository;
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
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.assetdashboard.global.security.RefreshTokenRepository;
import com.assetdashboard.global.security.RefreshTokenService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private TransactionRepository transactionRepository;
  @Mock private PortfolioSnapshotRepository portfolioSnapshotRepository;
  @Mock private EvidenceDocumentRepository evidenceDocumentRepository;
  @Mock private AgentTraceRunRepository agentTraceRunRepository;
  @Mock private AgentTraceSpanRepository agentTraceSpanRepository;
  @Mock private AgentEvaluationRecordRepository agentEvaluationRecordRepository;
  @Mock private LiveEvaluationBatchJobRepository liveEvaluationBatchJobRepository;
  @Mock private LiveEvaluationBatchCaseRepository liveEvaluationBatchCaseRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private Asset asset;

  private UserAccountService userAccountService;
  private User user;

  @BeforeEach
  void setUp() {
    userAccountService =
        new UserAccountService(
            userRepository,
            assetRepository,
            transactionRepository,
            portfolioSnapshotRepository,
            evidenceDocumentRepository,
            agentTraceRunRepository,
            agentTraceSpanRepository,
            agentEvaluationRecordRepository,
            liveEvaluationBatchJobRepository,
            liveEvaluationBatchCaseRepository,
            refreshTokenRepository,
            refreshTokenService,
            passwordEncoder);
    user = User.create("user@example.com", "encoded-old", "user");
    when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(user));
    Mockito.lenient().when(passwordEncoder.matches("old-password", "encoded-old")).thenReturn(true);
  }

  @Test
  void changePasswordRequiresTheCurrentPassword() {
    when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

    assertThatThrownBy(
            () ->
                userAccountService.changePassword(
                    7L, new ChangePasswordRequest("wrong", "new-password")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
  }

  @Test
  void deleteAccountRemovesTransactionsAssetsSnapshotsAndUser() {
    when(asset.getId()).thenReturn(11L);
    when(assetRepository.findAllByUserId(7L)).thenReturn(List.of(asset));
    when(agentTraceRunRepository.findIdsByUserId(7L)).thenReturn(List.of(31L));
    LiveEvaluationBatchJob batch = LiveEvaluationBatchJob.pending(7L, List.of("missing-fx"));
    org.springframework.test.util.ReflectionTestUtils.setField(batch, "id", 41L);
    when(liveEvaluationBatchJobRepository.findAllByUserId(7L)).thenReturn(List.of(batch));

    userAccountService.deleteAccount(7L, new DeleteAccountRequest("old-password", "DELETE"));

    InOrder deletionOrder =
        inOrder(
            agentEvaluationRecordRepository,
            liveEvaluationBatchCaseRepository,
            liveEvaluationBatchJobRepository,
            agentTraceSpanRepository,
            agentTraceRunRepository,
            evidenceDocumentRepository,
            transactionRepository,
            assetRepository,
            portfolioSnapshotRepository,
            userRepository);
    deletionOrder.verify(liveEvaluationBatchCaseRepository).deleteAllByBatchJobIdIn(List.of(41L));
    deletionOrder.verify(liveEvaluationBatchJobRepository).deleteAllByUserId(7L);
    deletionOrder.verify(agentEvaluationRecordRepository).deleteAllByRunIdIn(List.of(31L));
    deletionOrder.verify(agentTraceSpanRepository).deleteAllByRunIdIn(List.of(31L));
    deletionOrder.verify(agentTraceRunRepository).deleteAllByUserId(7L);
    deletionOrder.verify(evidenceDocumentRepository).deleteAllByUserId(7L);
    deletionOrder.verify(transactionRepository).deleteAllByAssetIdIn(List.of(11L));
    deletionOrder.verify(assetRepository).deleteAllByIdInBatch(List.of(11L));
    deletionOrder.verify(portfolioSnapshotRepository).deleteAllByUserId(7L);
    deletionOrder.verify(userRepository).delete(user);
  }

  @Test
  void deleteAccountRejectsMissingConfirmationBeforeDeletingData() {
    assertThatThrownBy(
            () -> userAccountService.deleteAccount(7L, new DeleteAccountRequest("old-password", "delete")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT));
  }
}
