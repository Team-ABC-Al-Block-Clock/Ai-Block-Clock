CREATE TABLE ledger_account
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_type VARCHAR(30)    NOT NULL,
    reference_id VARCHAR(100)   NOT NULL,
    balance      DECIMAL(41, 6) NOT NULL DEFAULT 0.000000,
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
    amount_delta          DECIMAL(41, 6) NOT NULL,
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

CREATE TABLE charge_request
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_reference_id        VARCHAR(100)   NOT NULL,
    sender_wallet_address    VARCHAR(42)    NOT NULL,
    chain_id                 BIGINT         NOT NULL,
    transaction_hash         VARCHAR(66)    NOT NULL,
    requested_deposit_amount DECIMAL(38, 6) NOT NULL,
    credited_abc_amount      DECIMAL(41, 6) NULL,
    status                   VARCHAR(30)    NOT NULL,
    status_reason            VARCHAR(50)    NULL,
    version                  BIGINT         NOT NULL DEFAULT 0,
    confirmed_at             DATETIME(6)    NULL,
    created_at               DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_charge_request_chain_hash
        UNIQUE (chain_id, transaction_hash),
    CONSTRAINT ck_charge_request_deposit_amount_positive
        CHECK (requested_deposit_amount > 0),
    CONSTRAINT ck_charge_request_abc_amount_positive
        CHECK (
            credited_abc_amount IS NULL
                OR credited_abc_amount > 0
            )
);

CREATE INDEX idx_charge_request_user_status
    ON charge_request (user_reference_id, status);

-- 공급자가 정산금을 자기 지갑으로 보내 달라고 한 출금 신청이다(PAY-023). 신청할 때 금액을 장부에서 묶는다(withdrawal-hold:{id}).
-- request_id 는 화면이 신청 버튼을 누를 때 만드는 번호다. 같은 사람이 같은 번호로 다시 보내면 새로 만들지 않는다.
-- wallet_address 는 신청할 때의 로그인 지갑을 옮겨 적는다. 지급(PAY-025)은 이 주소로 보낸다.
-- reviewer_id · reviewed_at · reject_reason 은 관리자가 승인 · 반려한 기록이다(PAY-024). 심사 전에는 비어 있다.
-- payout_* 는 승인된 출금을 보관 지갑에서 abcToken 으로 보낸 기록이다(PAY-025).
-- 서명한 거래와 그 해시를 보내기 전에 적는다. 보냈는지 모를 때 같은 거래를 그대로 다시 보내려고 원문도 둔다.
-- 서명된 거래는 체인에 퍼지면 누구나 볼 수 있는 값이라 비밀이 아니다. 한 거래가 두 출금에 쓰이지 않는다.
create table withdrawal
(
    id                      bigint auto_increment primary key,
    request_id              varchar(36)    not null,
    requester_id            bigint         not null,
    amount_abc              decimal(41, 6) not null,
    wallet_address          varchar(42)    not null,
    status                  varchar(20)    not null,
    reviewer_id             bigint         null,
    reviewed_at             datetime(6)    null,
    reject_reason           varchar(200)   null,
    payout_transaction_hash varchar(66)    null,
    payout_raw_transaction  text           null,
    payout_submitted_at     datetime(6)    null,
    paid_at                 datetime(6)    null,
    created_at              datetime(6)    not null,
    updated_at              datetime(6)    not null,
    constraint uq_withdrawal_requester_request unique (requester_id, request_id),
    constraint ck_withdrawal_amount_positive check (amount_abc > 0),
    constraint uq_withdrawal_payout_transaction_hash unique (payout_transaction_hash)
);

-- 처리 중인 출금이 있는지, 마지막 신청이 언제인지를 신청마다 사용자별로 본다.
create index idx_withdrawal_requester_status on withdrawal (requester_id, status);

-- 관리자가 심사 대기 목록을 오래된 신청부터 본다.
create index idx_withdrawal_status_created on withdrawal (status, created_at);
