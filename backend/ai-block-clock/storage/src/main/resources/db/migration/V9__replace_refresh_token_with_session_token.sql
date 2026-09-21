DROP TABLE refresh_token;

-- 인증 수단이 바뀌므로 기존 개발 세션은 재사용하지 않고 새 지갑 로그인을 요구한다.
DELETE FROM login_session;

ALTER TABLE login_session
    ADD COLUMN session_token_hash VARCHAR(64) NOT NULL;

ALTER TABLE login_session
    ADD CONSTRAINT uq_login_session_token_hash UNIQUE (session_token_hash);
