-- V3은 테이블 collation을 명시했지만 V6 평가 테이블은 당시 DB 기본값을 사용했다.
-- 두 trace_id를 비교하는 보존 쿼리가 서버 기본 collation과 무관하게 동작하도록 맞춘다.
ALTER TABLE live_evaluation_batch_cases
    MODIFY trace_id VARCHAR(36)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

CREATE INDEX idx_audit_created ON audit_logs (created_at);
CREATE INDEX idx_ai_run_created ON ai_agent_runs (created_at);
CREATE INDEX idx_live_eval_status_completed
    ON live_evaluation_batches (status, completed_at);
CREATE INDEX idx_live_eval_case_trace ON live_evaluation_batch_cases (trace_id);
