alter table execution
    add column reserved_abc decimal(41, 6) null;

-- 선차감이 이 칸보다 먼저 들어갔다. 이미 떼어 둔 실행은 장부의 예치 기록에서 금액을 옮겨 적는다.
-- 떼지 않은 실행은 비어 있는 채로 남는다.
update execution
   set reserved_abc = (select entry.amount_delta
                         from ledger_transaction ledger
                         join ledger_entry entry on entry.ledger_transaction_id = ledger.id
                         join ledger_account account on account.id = entry.ledger_account_id
                        where ledger.idempotency_key = concat('execution-reserve:', execution.id)
                          and account.account_type = 'EXECUTION_ESCROW');

-- 엔진에 보낸 단계마다 실측 원가에 곱할 배율(1 + 등급 할증)이다. 접수할 때 한 번 적고 고치지 않는다.
create table execution_charge_rate
(
    id               bigint auto_increment primary key,
    execution_id     varchar(36)   not null,
    step_id          varchar(16)   not null,
    price_multiplier decimal(6, 4) not null,

    constraint uq_execution_charge_rate_step unique (execution_id, step_id)
);
