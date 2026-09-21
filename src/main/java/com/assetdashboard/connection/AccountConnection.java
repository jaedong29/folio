package com.assetdashboard.connection;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_connections", uniqueConstraints = @UniqueConstraint(name = "uk_connection_owner_provider", columnNames = {"user_id", "provider"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountConnection {
  public enum Status { QUEUED, SYNCING, READY, ERROR }
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false) private Long userId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ConnectionProvider provider;
  @Column(nullable = false, columnDefinition = "TEXT") private String encryptedCredentials;
  @Column(length = 80) private String externalAccountId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
  @Column(columnDefinition = "MEDIUMTEXT") private String snapshotJson;
  private Instant lastSyncedAt;
  private Instant lastAttemptAt;
  @Column(nullable = false) private Instant nextSyncAt;
  @Column(length = 36) private String claimToken;
  @Column(length = 48) private String errorCode;
  @Version private long version;

  static AccountConnection create(Long userId, ConnectionProvider provider, String encrypted, Instant now) {
    AccountConnection c = new AccountConnection();
    c.userId = userId; c.provider = provider;
    c.replaceCredentials(encrypted, now);
    return c;
  }
  void replaceCredentials(String encrypted, Instant now) {
    encryptedCredentials = encrypted; externalAccountId = null;
    snapshotJson = null; lastSyncedAt = null; lastAttemptAt = null; claimToken = null;
    status = Status.QUEUED; nextSyncAt = now; errorCode = null;
  }
  void queue(Instant now) { status = Status.QUEUED; nextSyncAt = now; errorCode = null; }
  String claim(Instant now) {
    status = Status.SYNCING; lastAttemptAt = now;
    nextSyncAt = now.plusSeconds(120); claimToken = UUID.randomUUID().toString();
    return claimToken;
  }
  void complete(String json, String accountId, Instant now, int interval) {
    snapshotJson = json; externalAccountId = accountId; lastSyncedAt = now;
    status = Status.READY; errorCode = null; claimToken = null; nextSyncAt = now.plusSeconds(interval);
  }
  void fail(String code, Instant now, int interval) {
    status = Status.ERROR; errorCode = code; claimToken = null; nextSyncAt = now.plusSeconds(interval);
  }
}
