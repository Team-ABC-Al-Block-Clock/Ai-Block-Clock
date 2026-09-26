-- 실패한 실행이 누구 탓인지다. 실패한 실행마다 한 줄이고, 끝날 때 한 번 적고 고치지 않는다.
-- 실행 ID 가 PK 라 같은 실행의 판정을 두 번 적을 수 없다. 성공한 실행과 이 표보다 먼저 끝난 실행은 줄이 없다.
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
