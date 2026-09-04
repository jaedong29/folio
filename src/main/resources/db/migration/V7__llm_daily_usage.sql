CREATE TABLE llm_daily_usage (
    usage_date DATE NOT NULL,
    call_count BIGINT NOT NULL DEFAULT 0,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (usage_date)
);
