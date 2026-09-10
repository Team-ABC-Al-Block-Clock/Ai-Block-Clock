CREATE TABLE ledger_account
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_type VARCHAR(30)    NOT NULL,
    reference_id VARCHAR(100)   NOT NULL,
    balance      DECIMAL(38, 3) NOT NULL DEFAULT 0.000,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_ledger_account_type_reference
        UNIQUE (account_type, reference_id)
);

CREATE TABLE ledger_transaction
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type       VARCHAR(30)  NOT NULL,
    idempotency_key  VARCHAR(150) NOT NULL,
    reference_type   VARCHAR(30)  NOT NULL,
    reference_id     VARCHAR(100) NOT NULL,
    chain_id         BIGINT       NULL,
    transaction_hash VARCHAR(66)  NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_ledger_transaction_idempotency
        UNIQUE (idempotency_key),

    CONSTRAINT uq_ledger_transaction_chain_hash
        UNIQUE (chain_id, transaction_hash)
);

CREATE TABLE ledger_entry
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    ledger_transaction_id BIGINT         NOT NULL,
    ledger_account_id     BIGINT         NOT NULL,
    amount_delta          DECIMAL(38, 3) NOT NULL,
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_ledger_entry_transaction
        FOREIGN KEY (ledger_transaction_id)
            REFERENCES ledger_transaction (id),

    CONSTRAINT fk_ledger_entry_account
        FOREIGN KEY (ledger_account_id)
            REFERENCES ledger_account (id),

    CONSTRAINT ck_ledger_entry_amount_not_zero
        CHECK (amount_delta <> 0.000)
);

CREATE INDEX idx_ledger_entry_account
    ON ledger_entry (ledger_account_id);

CREATE INDEX idx_ledger_entry_transaction
    ON ledger_entry (ledger_transaction_id);
