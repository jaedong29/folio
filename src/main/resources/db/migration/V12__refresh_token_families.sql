CREATE TABLE refresh_token_families (
    family_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (family_id),
    KEY idx_refresh_token_family_user (user_id),
    KEY idx_refresh_token_family_expires (expires_at)
);

-- 기존 family는 현재 남아 있는 token 중 가장 늦은 만료 시각을 절대 만료로 삼아 배포 시 세션을 끊지 않는다.
-- 활성 token이 하나도 없는 family만 이미 폐기된 family로 이관한다.
INSERT INTO refresh_token_families (family_id, user_id, issued_at, expires_at, revoked_at)
SELECT family_id,
       MIN(user_id),
       MIN(issued_at),
       MAX(expires_at),
       CASE
           WHEN SUM(CASE WHEN revoked_at IS NULL THEN 1 ELSE 0 END) = 0 THEN MAX(revoked_at)
           ELSE NULL
       END
FROM refresh_tokens
GROUP BY family_id;
