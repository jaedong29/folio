CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    family_id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_user (user_id),
    KEY idx_refresh_token_family (family_id)
);
