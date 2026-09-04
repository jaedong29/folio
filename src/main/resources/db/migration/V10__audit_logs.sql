CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subject_user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_subject_created (subject_user_id, created_at),
    KEY idx_audit_action_created (action, created_at)
);
