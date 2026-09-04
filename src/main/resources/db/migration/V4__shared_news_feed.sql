CREATE TABLE news_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_key VARCHAR(80) NOT NULL,
    external_id VARCHAR(160) NOT NULL,
    category VARCHAR(24) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    trust VARCHAR(32) NOT NULL,
    title VARCHAR(300) NOT NULL,
    publisher VARCHAR(120) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    fetched_at DATETIME(6) NOT NULL,
    excerpt VARCHAR(1000),
    content LONGTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_news_source_external UNIQUE (source_key, external_id),
    KEY idx_news_published (published_at),
    KEY idx_news_category_published (category, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE news_item_symbols (
    news_item_id BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    PRIMARY KEY (news_item_id, symbol),
    KEY idx_news_symbol (symbol, news_item_id),
    CONSTRAINT fk_news_symbol_item FOREIGN KEY (news_item_id) REFERENCES news_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE news_item_topics (
    news_item_id BIGINT NOT NULL,
    topic VARCHAR(40) NOT NULL,
    PRIMARY KEY (news_item_id, topic),
    KEY idx_news_topic (topic, news_item_id),
    CONSTRAINT fk_news_topic_item FOREIGN KEY (news_item_id) REFERENCES news_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE news_refresh_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id VARCHAR(36) NOT NULL,
    source_key VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(6),
    completed_at DATETIME(6),
    discovered_count INT NOT NULL,
    inserted_count INT NOT NULL,
    updated_count INT NOT NULL,
    unchanged_count INT NOT NULL,
    error_code VARCHAR(80),
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_news_refresh_job UNIQUE (job_id),
    KEY idx_news_refresh_source_created (source_key, created_at),
    KEY idx_news_refresh_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
