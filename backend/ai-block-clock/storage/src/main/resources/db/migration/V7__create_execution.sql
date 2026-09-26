-- mock 은 모델을 부르지 않고 도는 실행인지를 접수할 때 적는다. 엔진 계약 v2.0 에서 받을 값이 엔진 원가와 끊겨,
-- 엔진 응답의 mock 칸 하나에만 기대면 그 칸이 빠졌을 때 가짜 결과물에 값을 받게 된다.
-- 이 값으로 mock 실행은 전부 돌려주고 등급 재료로도 세지 않는다.
create table execution
(
    id              varchar(36)    not null,
    workflow_id     varchar(36)    not null,
    requester_id    bigint         not null,
    run_id          varchar(64)    null,
    status          varchar(20)    not null,
    prompt_count    int            not null,
    created_at      datetime(6)    not null,
    updated_at      datetime(6)    not null,
    input_file_key  varchar(128)   null,
    input_file_name varchar(255)   null,
    input_file_size bigint         null,
    request_id      varchar(36)    not null,
    reserved_abc    decimal(41, 6) null,
    mock            boolean        not null default false,
    primary key (id),
    constraint uq_execution_requester_request unique (requester_id, request_id)
);

create index idx_execution_requester_created on execution (requester_id, created_at, id);
create index idx_execution_workflow on execution (workflow_id);

create table execution_result
(
    id           bigint       not null auto_increment,
    execution_id varchar(36)  not null,
    format       varchar(20)  not null,
    file_key     varchar(128) not null,
    file_size    bigint       not null,
    primary key (id)
);

create unique index uk_execution_result_format on execution_result (execution_id, format);

-- 엔진에 보낸 단계마다 실측 원가에 곱할 배율(1 + 등급 할증)과 받을 값(원가 × 배율)이다. 접수할 때 한 번 적고 고치지 않는다.
-- 엔진 계약 v2.0 에서 엔진이 ABC 금액을 주지 않게 되어, 끝난 뒤 받을 값을 접수할 때 정한 단가로 센다.
create table execution_charge_rate
(
    id               bigint auto_increment primary key,
    execution_id     varchar(36)    not null,
    step_id          varchar(16)    not null,
    price_multiplier decimal(6, 4)  not null,
    unit_price       decimal(41, 6) null,
    constraint uq_execution_charge_rate_step unique (execution_id, step_id)
);

-- 실패한 실행이 누구 탓인지다. 실패한 실행마다 한 줄이고, 끝날 때 한 번 적고 고치지 않는다.
-- 실행 ID 가 PK 라 같은 실행의 판정을 두 번 적을 수 없다. 성공한 실행은 줄이 없다.
-- 공급자 · 에이전트는 공급자 탓일 때만 적는다. 성공률 집계(PAY-018)와 정산(PAY-012)이 읽는다.
create table execution_failure
(
    execution_id         varchar(36) not null primary key,
    failed_step_id       varchar(16) null,
    failure_code         varchar(64) null,
    fault                varchar(20) not null,
    affects_success_rate boolean     not null,
    supplier_id          bigint      null,
    agent_id             varchar(36) null
);

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

-- 정산 내역 · 수익 통계(PAY-013/014)는 늘 "이 공급자의 것을 기간 안에서 최신순으로" 읽는다.
create index idx_execution_settlement_supplier_created
    on execution_settlement (supplier_id, created_at);
