-- 공급자가 정산금을 자기 지갑으로 보내 달라고 한 출금 신청이다(PAY-023). 신청할 때 금액을 장부에서 묶는다(withdrawal-hold:{id}).
-- request_id 는 화면이 신청 버튼을 누를 때 만드는 번호다. 같은 사람이 같은 번호로 다시 보내면 새로 만들지 않는다.
-- wallet_address 는 신청할 때의 로그인 지갑을 옮겨 적는다. 지급(PAY-025)은 이 주소로 보낸다.
create table withdrawal
(
    id             bigint auto_increment primary key,
    request_id     varchar(36)    not null,
    requester_id   bigint         not null,
    amount_abc     decimal(41, 6) not null,
    wallet_address varchar(42)    not null,
    status         varchar(20)    not null,
    created_at     datetime(6)    not null,
    updated_at     datetime(6)    not null,

    constraint uq_withdrawal_requester_request unique (requester_id, request_id),
    constraint ck_withdrawal_amount_positive check (amount_abc > 0)
);

-- 처리 중인 출금이 있는지, 마지막 신청이 언제인지를 신청마다 사용자별로 본다.
create index idx_withdrawal_requester_status on withdrawal (requester_id, status);
