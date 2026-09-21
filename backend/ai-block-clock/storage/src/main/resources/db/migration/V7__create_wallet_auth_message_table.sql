CREATE TABLE wallet_auth_message
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_address VARCHAR(42) NOT NULL,
    nonce          VARCHAR(64) NOT NULL,
    message        TEXT        NOT NULL,
    issued_at      DATETIME(6) NOT NULL,
    expires_at     DATETIME(6) NOT NULL,
    used_at        DATETIME(6) NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,

    CONSTRAINT uq_wallet_auth_message_wallet_address UNIQUE (wallet_address)
);
