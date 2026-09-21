-- Independent read-only provider snapshots; manual assets/transactions are never overwritten.
CREATE TABLE account_connections (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(24) NOT NULL,
    encrypted_credentials TEXT NOT NULL,
    external_account_id VARCHAR(80) NULL,
    status VARCHAR(16) NOT NULL,
    snapshot_json MEDIUMTEXT NULL,
    last_synced_at DATETIME(6) NULL,
    last_attempt_at DATETIME(6) NULL,
    next_sync_at DATETIME(6) NOT NULL,
    claim_token VARCHAR(36) NULL,
    error_code VARCHAR(48) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_connection_owner_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_connection_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_connection_due (next_sync_at)
);
