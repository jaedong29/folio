CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    quantity DECIMAL(20, 8) NOT NULL,
    initial_quantity DECIMAL(20, 8),
    position_corrected_at DATETIME(6),
    avg_price DECIMAL(20, 8),
    avg_price_original DECIMAL(20, 8),
    initial_avg_price DECIMAL(20, 8),
    initial_avg_price_original DECIMAL(20, 8),
    current_price DECIMAL(20, 8),
    price_updated_at DATETIME(6),
    currency VARCHAR(10) NOT NULL,
    exchange_rate DECIMAL(10, 4),
    exchange_rate_updated_at DATETIME(6),
    realized_pnl DECIMAL(20, 8) NOT NULL,
    source VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_asset_user_type_symbol UNIQUE (user_id, type, symbol),
    KEY idx_asset_user_type (user_id, type),
    CONSTRAINT fk_assets_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    asset_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    quantity DECIMAL(20, 8) NOT NULL,
    price DECIMAL(20, 8),
    exchange_rate DECIMAL(10, 4),
    settlement_asset_id BIGINT,
    settlement_amount DECIMAL(20, 8),
    memo VARCHAR(255),
    traded_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tx_asset_traded_at (asset_id, traded_at DESC),
    CONSTRAINT fk_transactions_asset FOREIGN KEY (asset_id) REFERENCES assets (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE portfolio_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    total_value_krw DECIMAL(20, 2) NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    demo_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_snapshot_user_date UNIQUE (user_id, snapshot_date),
    KEY idx_snapshot_user_date (user_id, snapshot_date),
    CONSTRAINT fk_snapshots_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
