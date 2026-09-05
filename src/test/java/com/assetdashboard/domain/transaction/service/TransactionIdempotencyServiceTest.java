package com.assetdashboard.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assetdashboard.domain.asset.entity.Asset;
import com.assetdashboard.domain.asset.entity.AssetType;
import com.assetdashboard.domain.asset.service.AssetService;
import com.assetdashboard.domain.transaction.dto.TransactionResponse;
import com.assetdashboard.domain.transaction.entity.IdempotencyKey;
import com.assetdashboard.domain.transaction.entity.Transaction;
import com.assetdashboard.domain.transaction.repository.IdempotencyKeyRepository;
import com.assetdashboard.domain.transaction.repository.TransactionRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class TransactionIdempotencyServiceTest {

  private final IdempotencyKeyRepository repository = mock(IdempotencyKeyRepository.class);
  private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
  private final AssetService assetService = mock(AssetService.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final TransactionIdempotencyService service =
      new TransactionIdempotencyService(
          repository, transactionRepository, assetService, clock, objectMapper);

  @Test
  void firstRequestClaimsAndReturnsEmpty() {
    when(repository.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.empty());

    Optional<com.assetdashboard.domain.transaction.dto.TransactionResponse> result =
        service.checkAndClaim(7L, "key-1", "fp-1");

    assertThat(result).isEmpty();
    ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("key-1");
    assertThat(captor.getValue().getRequestFingerprint()).isEqualTo("fp-1");
  }

  @Test
  void completedDuplicateReturnsCachedResponseWithoutReexecuting() {
    IdempotencyKey record = IdempotencyKey.claim(7L, "key-1", "fp-1");
    when(repository.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.of(record));

    Transaction tx =
        Transaction.createDeposit(
            10L, BigDecimal.TEN, BigDecimal.ONE, "memo", LocalDateTime.now());
    ReflectionTestUtils.setField(tx, "id", 42L);
    Asset asset = Asset.create(7L, AssetType.CASH, "KRW", "원화", "KRW");
    TransactionResponse firstResponse = TransactionResponse.from(tx, asset);

    service.complete(7L, "key-1", firstResponse);

    Optional<TransactionResponse> result =
        service.checkAndClaim(7L, "key-1", "fp-1");

    assertThat(result).contains(firstResponse);
    verify(repository, never()).saveAndFlush(any());
    verifyNoInteractions(transactionRepository, assetService);
  }

  @Test
  void completedKeyFromBeforeV11FallsBackToRebuildingTheResponse() {
    IdempotencyKey legacyRecord = IdempotencyKey.claim(7L, "legacy-key", "fp-1");
    ReflectionTestUtils.setField(legacyRecord, "resultTransactionId", 42L);
    when(repository.findByUserIdAndIdempotencyKey(7L, "legacy-key"))
        .thenReturn(Optional.of(legacyRecord));

    Transaction tx =
        Transaction.createDeposit(
            10L, BigDecimal.TEN, BigDecimal.ONE, "memo", LocalDateTime.now());
    ReflectionTestUtils.setField(tx, "id", 42L);
    when(transactionRepository.findById(42L)).thenReturn(Optional.of(tx));
    Asset asset = Asset.create(7L, AssetType.CASH, "KRW", "원화", "KRW");
    when(assetService.getOwnedAsset(7L, 10L)).thenReturn(asset);

    Optional<TransactionResponse> result =
        service.checkAndClaim(7L, "legacy-key", "fp-1");

    assertThat(result).isPresent();
    assertThat(result.get().transactionId()).isEqualTo(42L);
  }

  @Test
  void sameKeyWithDifferentRequestIsRejectedAsReuse() {
    IdempotencyKey record = IdempotencyKey.claim(7L, "key-1", "fp-1");
    record.complete(42L, "{}");
    when(repository.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.of(record));

    assertThatThrownBy(() -> service.checkAndClaim(7L, "key-1", "fp-DIFFERENT"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
  }

  @Test
  void inFlightDuplicateWithoutResultYetIsRejectedAsInProgress() {
    IdempotencyKey record = IdempotencyKey.claim(7L, "key-1", "fp-1");
    when(repository.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.of(record));

    assertThatThrownBy(() -> service.checkAndClaim(7L, "key-1", "fp-1"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS));
  }

  @Test
  void raceOnClaimInsertIsTreatedAsInProgress() {
    when(repository.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.empty());
    when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service.checkAndClaim(7L, "key-1", "fp-1"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS));
  }

  @Test
  void fingerprintIsDeterministicAndSensitiveToEveryInput() {
    String base = service.fingerprint("buy", 42L, "quantity=1");

    assertThat(service.fingerprint("buy", 42L, "quantity=1")).isEqualTo(base);
    assertThat(service.fingerprint("sell", 42L, "quantity=1")).isNotEqualTo(base);
    assertThat(service.fingerprint("buy", 43L, "quantity=1")).isNotEqualTo(base);
    assertThat(service.fingerprint("buy", 42L, "quantity=2")).isNotEqualTo(base);
  }

  @Test
  void completeStoresTheTransactionIdAndSerializedResponse() throws Exception {
    IdempotencyKey record = IdempotencyKey.claim(7L, "key-1", "fp-1");
    when(repository.findByUserIdAndIdempotencyKey(7L, "key-1")).thenReturn(Optional.of(record));
    Transaction tx =
        Transaction.createDeposit(
            10L, BigDecimal.TEN, BigDecimal.ONE, "memo", LocalDateTime.now());
    ReflectionTestUtils.setField(tx, "id", 99L);
    TransactionResponse response =
        TransactionResponse.from(tx, Asset.create(7L, AssetType.CASH, "KRW", "원화", "KRW"));

    service.complete(7L, "key-1", response);

    assertThat(record.getResultTransactionId()).isEqualTo(99L);
    assertThat(objectMapper.readValue(record.getResultResponseJson(), TransactionResponse.class))
        .isEqualTo(response);
  }

  @Test
  void completeWithoutAClaimFailsInsteadOfSilentlyLosingTheCachedResponse() {
    when(repository.findByUserIdAndIdempotencyKey(7L, "missing-key"))
        .thenReturn(Optional.empty());
    TransactionResponse response =
        new TransactionResponse(
            99L, null, null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> service.complete(7L, "missing-key", response))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("claim");
  }

  @Test
  void evictionDeletesRowsOlderThanRetentionWindow() {
    service.evictExpiredKeys();

    verify(repository)
        .deleteAllByCreatedAtBefore(LocalDateTime.parse("2026-09-04T00:00:00"));
  }
}
