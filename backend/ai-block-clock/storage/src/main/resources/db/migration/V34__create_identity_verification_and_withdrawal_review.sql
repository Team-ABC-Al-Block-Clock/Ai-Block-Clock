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

-- 관리자가 출금 신청을 승인 · 반려한 기록이다(PAY-024). 심사 전에는 비어 있다.
alter table withdrawal
    add column reviewer_id bigint null after status;

alter table withdrawal
    add column reviewed_at datetime(6) null after reviewer_id;

alter table withdrawal
    add column reject_reason varchar(200) null after reviewed_at;

-- 관리자가 심사 대기 목록을 오래된 신청부터 본다.
create index idx_withdrawal_status_created on withdrawal (status, created_at);
