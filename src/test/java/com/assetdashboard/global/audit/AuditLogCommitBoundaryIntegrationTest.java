package com.assetdashboard.global.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetdashboard.domain.user.dto.ChangePasswordRequest;
import com.assetdashboard.domain.user.dto.DeleteAccountRequest;
import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.domain.user.service.UserAccountService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 감사 기록이 실제 업무 커밋과 함께 남거나 롤백되는지 확인한다.
 *
 * <p>일부러 클래스 단위 {@code @Transactional}을 쓰지 않는다. 각 서비스 호출이 실제 커밋 경계를 지난 뒤 별도
 * 조회로 결과를 확인해야 감사 기록만 먼저 커밋되는 전파 오류를 잡을 수 있다.
 */
@SpringBootTest
class AuditLogCommitBoundaryIntegrationTest {

  @Autowired private AuditLogService auditLogService;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private UserAccountService userAccountService;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private TransactionTemplate transactionTemplate;

  private Long userId;

  @AfterEach
  void cleanUp() {
    if (userId == null) {
      return;
    }
    auditLogRepository.deleteAll(
        auditLogRepository.findAllBySubjectUserIdOrderByCreatedAtAsc(userId));
    userRepository.findById(userId).ifPresent(userRepository::delete);
  }

  @Test
  void passwordChangeAndAuditRecordCommitTogether() {
    User user = saveUser();

    userAccountService.changePassword(
        userId, new ChangePasswordRequest("old-password", "new-password"));

    User changed = userRepository.findById(userId).orElseThrow();
    assertThat(passwordEncoder.matches("new-password", changed.getPassword())).isTrue();
    assertThat(actionsForUser()).containsExactly(AuditAction.PASSWORD_CHANGED);
  }

  @Test
  void accountDeletionKeepsOnlyTheNonForeignKeyAuditRecord() {
    saveUser();

    userAccountService.deleteAccount(
        userId, new DeleteAccountRequest("old-password", "DELETE"));

    assertThat(userRepository.findById(userId)).isEmpty();
    assertThat(actionsForUser()).containsExactly(AuditAction.ACCOUNT_DELETED);
  }

  @Test
  void rolledBackBusinessTransactionDoesNotLeaveASuccessAuditRecord() {
    userId = 8_000_000L + Math.floorMod(System.nanoTime(), 1_000_000L);

    transactionTemplate.executeWithoutResult(
        status -> {
          auditLogService.record(userId, AuditAction.PASSWORD_CHANGED);
          status.setRollbackOnly();
        });

    assertThat(actionsForUser()).isEmpty();
  }

  @Test
  void auditRecordCannotBeWrittenWithoutABusinessTransaction() {
    userId = 9_000_000L + Math.floorMod(System.nanoTime(), 1_000_000L);

    assertThatThrownBy(
            () -> auditLogService.record(userId, AuditAction.PASSWORD_CHANGED))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(actionsForUser()).isEmpty();
  }

  private User saveUser() {
    User user =
        userRepository.saveAndFlush(
            User.create(
                "audit-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("old-password"),
                "audit-user"));
    userId = user.getId();
    return user;
  }

  private List<AuditAction> actionsForUser() {
    return auditLogRepository.findAllBySubjectUserIdOrderByCreatedAtAsc(userId).stream()
        .map(AuditLog::getAction)
        .toList();
  }
}
