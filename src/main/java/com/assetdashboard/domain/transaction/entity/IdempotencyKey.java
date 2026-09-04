package com.assetdashboard.domain.transaction.entity;

import com.assetdashboard.global.common.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매수·매도·입금·출금 요청을 한 번만 실행하기 위한 멱등성 claim.
 *
 * <p>같은 사용자가 같은 {@code idempotencyKey}로 다시 요청하면, 원래 거래를 다시 만들지 않고 그 결과를 그대로
 * 돌려준다. {@code requestFingerprint}는 같은 키가 다른 요청 내용에 잘못 재사용되는 것을 감지한다.
 * {@code resultTransactionId}가 아직 null이면 같은 요청이 동시에 처리 중이라는 뜻이다.
 */
@Getter
@Entity
@Table(
    name = "idempotency_keys",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_idempotency_user_key",
            columnNames = {"user_id", "idempotency_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "idempotency_key", nullable = false, length = 255)
  private String idempotencyKey;

  @Column(name = "request_fingerprint", nullable = false, length = 64, columnDefinition = "CHAR(64)")
  private String requestFingerprint;

  @Column(name = "result_transaction_id")
  private Long resultTransactionId;

  private IdempotencyKey(Long userId, String idempotencyKey, String requestFingerprint) {
    this.userId = userId;
    this.idempotencyKey = idempotencyKey;
    this.requestFingerprint = requestFingerprint;
  }

  public static IdempotencyKey claim(Long userId, String idempotencyKey, String requestFingerprint) {
    return new IdempotencyKey(userId, idempotencyKey, requestFingerprint);
  }

  public boolean matchesFingerprint(String otherFingerprint) {
    return this.requestFingerprint.equals(otherFingerprint);
  }

  public void complete(Long transactionId) {
    this.resultTransactionId = transactionId;
  }
}
