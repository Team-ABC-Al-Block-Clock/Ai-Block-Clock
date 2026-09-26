CREATE TABLE app_user
(
    id                           BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_address               VARCHAR(42) NOT NULL,
    nickname                     VARCHAR(20) NULL,
    introduction                 VARCHAR(40) NULL,
    terms_agreed_at              DATETIME(6) NULL,
    recovery_notice_confirmed_at DATETIME(6) NULL,
    onboarding_completed_at      DATETIME(6) NULL,
    version                      BIGINT      NOT NULL DEFAULT 0,
    created_at                   DATETIME(6) NOT NULL,
    updated_at                   DATETIME(6) NOT NULL,
    CONSTRAINT uq_app_user_wallet_address UNIQUE (wallet_address)
);

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

-- 출금 전에 받는 본인 확인이다(ACC-004). 사용자마다 한 줄이고, 반려되면 같은 줄을 고쳐 다시 제출한다.
-- submitted_at 은 마지막으로 제출한 시각이다. 관리자는 오래 기다린 것부터 본다.
-- reviewer_id · reviewed_at · reject_reason 은 관리자가 승인 · 반려한 뒤에만 있다. 다시 제출하면 비운다.
create table identity_verification
(
    id            bigint auto_increment primary key,
    user_id       bigint       not null,
    legal_name    varchar(50)  not null,
    birth_date    date         not null,
    status        varchar(20)  not null,
    submitted_at  datetime(6)  not null,
    reviewer_id   bigint       null,
    reviewed_at   datetime(6)  null,
    reject_reason varchar(200) null,
    created_at    datetime(6)  not null,
    updated_at    datetime(6)  not null,
    constraint uq_identity_verification_user unique (user_id)
);

create index idx_identity_verification_status on identity_verification (status, submitted_at);
