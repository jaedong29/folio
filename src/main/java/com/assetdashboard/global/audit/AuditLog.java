package com.assetdashboard.global.audit;

import com.assetdashboard.global.common.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 민감 액션의 성공 사실만 남기는 append-only 감사 기록. */
@Getter
@Entity
@Table(
    name = "audit_logs",
    indexes = {
      @Index(name = "idx_audit_subject_created", columnList = "subject_user_id, created_at"),
      @Index(name = "idx_audit_action_created", columnList = "action, created_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 탈퇴 뒤에도 기록을 보존해야 하므로 users 외래키를 두지 않는 내부 식별자다. */
  @Column(name = "subject_user_id", nullable = false)
  private Long subjectUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private AuditAction action;

  private AuditLog(Long subjectUserId, AuditAction action) {
    this.subjectUserId = Objects.requireNonNull(subjectUserId);
    this.action = Objects.requireNonNull(action);
  }

  public static AuditLog record(Long subjectUserId, AuditAction action) {
    return new AuditLog(subjectUserId, action);
  }
}
