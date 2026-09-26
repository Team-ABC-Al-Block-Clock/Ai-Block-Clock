-- 끝난 실행에서 성공한 단계마다 그 주인에게 적립한 정산금이다. 실행을 닫을 때 한 번 적고 고치지 않는다.
-- 금액의 합은 같은 실행의 장부 정산 거래(execution-settlement:{실행ID})와 같다. 정산 내역 · 수익 통계(PAY-013/014)가 읽는다.
-- node_name 은 실행할 때 보낸 노드 이름이다. agent_id 는 에이전트 안의 단계일 때만 채운다.
create table execution_settlement
(
    id           bigint auto_increment primary key,
    execution_id varchar(36)    not null,
    step_id      varchar(16)    not null,
    node_name    varchar(40)    null,
    supplier_id  bigint         not null,
    agent_id     varchar(36)    null,
    amount_abc   decimal(41, 6) not null,
    created_at   datetime(6)    not null,

    constraint uq_execution_settlement_step unique (execution_id, step_id)
);
