package com.assetdashboard.connection;

import com.assetdashboard.domain.user.entity.User;
import com.assetdashboard.domain.user.repository.UserRepository;
import com.assetdashboard.global.exception.BusinessException;
import com.assetdashboard.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConnectionLifecycleService {
  private final AccountConnectionRepository repository;
  private final UserRepository users;
  private final EntityManager entityManager;
  private final ConnectionProperties properties;
  private final ConnectionCrypto crypto;
  private final ObjectMapper mapper;
  private final Clock clock;

  public record Claim(Long id, Long userId, ConnectionProvider provider, String encrypted,
      String externalAccountId, String token) {
    @Override public String toString() { return "ConnectionClaim[id=" + id + "]"; }
  }

  @Transactional
  public void connect(Long userId, ConnectionProvider provider, ConnectionCredentials credentials) {
    ensureEnabled();
    // Serialize one user's create/replace requests without an HTTP call under the lock.
    if (entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE) == null) unauthorized();
    String encrypted = crypto.encrypt(userId, provider, credentials);
    AccountConnection c = repository.findByUserIdAndProvider(userId, provider).orElse(null);
    if (c == null) repository.save(AccountConnection.create(userId, provider, encrypted, clock.instant()));
    else {
      c = repository.findLockedById(c.getId()).orElseThrow();
      if (c.getLastAttemptAt() != null && c.getLastAttemptAt().isAfter(clock.instant().minusSeconds(60))) {
        throw new BusinessException(ErrorCode.CONNECTION_SYNC_THROTTLED);
      }
      c.replaceCredentials(encrypted, clock.instant());
    }
  }

  @Transactional
  public void queue(Long userId, Long id) {
    ensureEnabled();
    AccountConnection c = ownedLocked(userId, id);
    Instant now = clock.instant();
    if (c.getStatus() == AccountConnection.Status.QUEUED) return;
    if ((c.getStatus() == AccountConnection.Status.SYNCING && c.getNextSyncAt().isAfter(now))
        || (c.getLastAttemptAt() != null && c.getLastAttemptAt().isAfter(now.minusSeconds(60)))) {
      throw new BusinessException(ErrorCode.CONNECTION_SYNC_THROTTLED);
    }
    c.queue(now);
  }

  @Transactional
  public void disconnect(Long userId, Long id) { repository.delete(ownedLocked(userId, id)); }

  @Transactional
  public Optional<Claim> claimNext() {
    if (!properties.enabled()) return Optional.empty();
    Instant now = clock.instant();
    for (Long id : repository.findDueIds(now, PageRequest.of(0, 1))) {
      AccountConnection c = repository.findLockedById(id).orElse(null);
      if (c == null || c.getNextSyncAt().isAfter(now)) continue;
      return Optional.of(new Claim(c.getId(), c.getUserId(), c.getProvider(), c.getEncryptedCredentials(),
          c.getExternalAccountId(), c.claim(now)));
    }
    return Optional.empty();
  }

  @Transactional
  public void complete(Claim claim, ConnectionProviderClient.Result result) {
    AccountConnection c = current(claim);
    if (c == null) return; // A removed/replaced connection cannot be resurrected by an old response.
    try {
      c.complete(mapper.writeValueAsString(result.snapshot()), result.accountId(), clock.instant(), properties.syncIntervalSeconds());
    } catch (Exception e) { throw new IllegalStateException("Could not store connection snapshot"); }
  }

  @Transactional
  public void fail(Claim claim, String code) {
    AccountConnection c = current(claim);
    if (c != null) c.fail(code, clock.instant(), properties.syncIntervalSeconds());
  }

  public void ensureUser(Long userId) { if (!users.existsById(userId)) unauthorized(); }
  private AccountConnection current(Claim claim) {
    AccountConnection c = repository.findLockedById(claim.id()).orElse(null);
    return c != null && Objects.equals(c.getClaimToken(), claim.token()) ? c : null;
  }
  private AccountConnection ownedLocked(Long userId, Long id) {
    AccountConnection c = repository.findLockedById(id).orElse(null);
    if (c == null || !c.getUserId().equals(userId)) throw new BusinessException(ErrorCode.CONNECTION_NOT_FOUND);
    return c;
  }
  private void ensureEnabled() {
    if (!properties.enabled()) throw new BusinessException(ErrorCode.CONNECTIONS_DISABLED);
  }
  private static void unauthorized() { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
}
