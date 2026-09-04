package com.assetdashboard.domain.transaction.service;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.domain.transaction.dto.TransactionResponse;
import com.assetdashboard.domain.transaction.entity.IdempotencyKey;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.repository.IdempotencyKeyRepository;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매수·매도·입금·출금이 네트워크 재시도나 중복 클릭으로 두 번 실행되지 않게 한다.
 *
 * <p>claim과 완료 기록은 호출자({@link TransactionService})의 트랜잭션에 그대로 참여한다 — 거래 자체가
 * 실패하면 claim도 함께 롤백되어야, 사용자가 같은 키로 (또는 고친 요청으로) 다시 시도할 수 있기 때문이다.
 * Refresh Token 재사용 탐지와 달리 여기서는 "실패해도 흔적을 남긴다"가 아니라 "성공한 것만 기억한다"가
 * 맞는 정책이라 별도 트랜잭션 분리가 필요 없다.
 */
@Service
@RequiredArgsConstructor
public class TransactionIdempotencyService {

  private static final long RETENTION_HOURS = 24;

  private final IdempotencyKeyRepository repository;
  private final TransactionRepository transactionRepository;
  private final AssetService assetService;
  private final Clock clock;

  /**
   * 같은 요청이 이미 완료됐으면 그 결과를 돌려주고, 처음 보는 요청이면 claim만 남기고 빈 값을 돌려준다.
   *
   * @throws BusinessException 같은 키가 다른 요청에 쓰였으면 {@code IDEMPOTENCY_KEY_REUSED}, 같은 요청이
   *     아직 처리 중이면 {@code IDEMPOTENCY_KEY_IN_PROGRESS}
   */
  @Transactional
  public Optional<TransactionResponse> checkAndClaim(
      Long userId, String idempotencyKey, String requestFingerprint) {
    Optional<IdempotencyKey> existing =
        repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    if (existing.isPresent()) {
      IdempotencyKey record = existing.get();
      if (!record.matchesFingerprint(requestFingerprint)) {
        throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
      }
      if (record.getResultTransactionId() == null) {
        throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS);
      }
      return Optional.of(rebuildResponse(userId, record.getResultTransactionId()));
    }
    try {
      repository.saveAndFlush(IdempotencyKey.claim(userId, idempotencyKey, requestFingerprint));
    } catch (DataIntegrityViolationException e) {
      // 이 순간 사이에 같은 키로 다른 요청이 먼저 claim했다.
      throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS);
    }
    return Optional.empty();
  }

  @Transactional
  public void complete(Long userId, String idempotencyKey, Long transactionId) {
    repository
        .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
        .ifPresent(record -> record.complete(transactionId));
  }

  /** 연산 종류·자산·요청 필드가 하나라도 다르면 다른 fingerprint가 나오도록 한다. */
  public String fingerprint(String operation, Long assetId, Object request) {
    String raw = operation + "|" + assetId + "|" + request;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM이 SHA-256을 지원하지 않습니다.", e);
    }
  }

  @Scheduled(fixedRate = 3_600_000)
  @Transactional
  public void evictExpiredKeys() {
    repository.deleteAllByCreatedAtBefore(LocalDateTime.now(clock).minusHours(RETENTION_HOURS));
  }

  private TransactionResponse rebuildResponse(Long userId, Long transactionId) {
    Transaction tx =
        transactionRepository
            .findById(transactionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    Asset asset = assetService.getOwnedAsset(userId, tx.getAssetId());
    return TransactionResponse.from(tx, asset);
  }
}
