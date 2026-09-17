CREATE TABLE login_session
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_login_session_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE refresh_token
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id         BIGINT      NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    expires_at         DATETIME(6) NOT NULL,
    used_at            DATETIME(6) NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,

    CONSTRAINT uq_refresh_token_hash
        UNIQUE (refresh_token_hash),

    CONSTRAINT fk_refresh_token_session
        FOREIGN KEY (session_id) REFERENCES login_session (id)
);
