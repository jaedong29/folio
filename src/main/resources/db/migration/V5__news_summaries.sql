ALTER TABLE news_items
    ADD COLUMN summary_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER content_hash,
    ADD COLUMN summary_ko VARCHAR(500) NULL AFTER summary_status,
    ADD COLUMN significance_ko VARCHAR(300) NULL AFTER summary_ko,
    ADD COLUMN summary_model VARCHAR(120) NULL AFTER significance_ko,
    ADD COLUMN summary_prompt_version VARCHAR(40) NULL AFTER summary_model,
    ADD COLUMN summarized_content_hash CHAR(64) NULL AFTER summary_prompt_version,
    ADD COLUMN summary_error_code VARCHAR(80) NULL AFTER summarized_content_hash,
    ADD COLUMN summary_latency_ms BIGINT NULL AFTER summary_error_code,
    ADD COLUMN summary_input_tokens BIGINT NULL AFTER summary_latency_ms,
    ADD COLUMN summary_output_tokens BIGINT NULL AFTER summary_input_tokens,
    ADD COLUMN summary_updated_at DATETIME(6) NULL AFTER summary_output_tokens,
    ADD KEY idx_news_summary_status_published (summary_status, published_at);
