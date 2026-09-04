CREATE TABLE ai_agent_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trace_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    case_id VARCHAR(100),
    question_hash CHAR(64) NOT NULL,
    answer_hash CHAR(64),
    prompt_version VARCHAR(80) NOT NULL,
    model VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    conclusion VARCHAR(20),
    latency_ms BIGINT NOT NULL,
    input_tokens BIGINT,
    output_tokens BIGINT,
    hard_failure BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_run_trace UNIQUE (trace_id),
    KEY idx_ai_run_user_created (user_id, created_at),
    CONSTRAINT fk_ai_run_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_agent_spans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    span_id VARCHAR(64) NOT NULL,
    parent_span_id VARCHAR(64),
    step_type VARCHAR(20) NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    latency_ms BIGINT NOT NULL,
    error_code VARCHAR(80),
    reference_ids VARCHAR(2000),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_span_run_span UNIQUE (run_id, span_id),
    KEY idx_ai_span_run_parent (run_id, parent_span_id),
    CONSTRAINT fk_ai_span_run FOREIGN KEY (run_id) REFERENCES ai_agent_runs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_evaluation_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    case_id VARCHAR(100) NOT NULL,
    scorer_version VARCHAR(40) NOT NULL,
    passed BOOLEAN NOT NULL,
    hard_failure BOOLEAN NOT NULL,
    failure_codes VARCHAR(1000),
    failure_details VARCHAR(4000),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_eval_run UNIQUE (run_id),
    CONSTRAINT fk_ai_eval_run FOREIGN KEY (run_id) REFERENCES ai_agent_runs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
