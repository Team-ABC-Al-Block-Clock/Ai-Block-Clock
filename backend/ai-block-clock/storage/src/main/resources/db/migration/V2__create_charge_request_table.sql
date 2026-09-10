CREATE TABLE charge_request
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_reference_id     VARCHAR(100)   NOT NULL,
    sender_wallet_address VARCHAR(42)    NOT NULL,
    chain_id              BIGINT         NOT NULL,
    transaction_hash      VARCHAR(66)    NOT NULL,
    requested_usdc_amount DECIMAL(38, 6) NOT NULL,
    credited_abc_amount   DECIMAL(38, 3) NULL,
    status                VARCHAR(30)    NOT NULL,
    status_reason         VARCHAR(50)    NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    confirmed_at          DATETIME(6)    NULL,
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_charge_request_chain_hash
        UNIQUE (chain_id, transaction_hash),

    CONSTRAINT ck_charge_request_usdc_amount_positive
        CHECK (requested_usdc_amount > 0),

    CONSTRAINT ck_charge_request_abc_amount_positive
        CHECK (
            credited_abc_amount IS NULL
                OR credited_abc_amount > 0
            )
);

CREATE INDEX idx_charge_request_user_status
    ON charge_request (user_reference_id, status);
