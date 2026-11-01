-- 정산을 다툴 때 꺼내 보는 근거다. 실행의 핵심 흐름(닫기 · 환불 · 정산)과 따로, 닫는 트랜잭션이 커밋된 뒤 근거 스레드가 적는다.
-- 적다 실패하거나 그 사이 서버가 내려가 빠진 근거는 백필이 채운다(ExecutionEvidenceRecorder). 한 번 적고 고치지 않는다.

-- 단계마다 엔진이 알린 사실(상태 · 건너뛴 이유 · 토큰 · 달러 원가 · 재시도 · 고쳐 쓰기)과 우리가 내린 판단(단가 · 적립액)이다.
-- 정산 내역(execution_settlement)은 받은 단계만 있어 "왜 이 단계는 안 받았나" 를 답하지 못했다. 여기에는 돈 안 받은 단계도 있다.
-- owner_id 는 엔진이 돌려준 값 그대로다(우리가 보낸 사용자 번호). started_at · finished_at 도 엔진이 준 문자열 그대로다.
create table execution_step
(
    id            bigint auto_increment primary key,
    execution_id  varchar(36)    not null,
    step_id       varchar(64)    not null,
    node_name     varchar(100)   null,
    step_type     varchar(20)    null,
    status        varchar(20)    null,
    skip_reason   varchar(64)    null,
    branch_taken  boolean        null,
    owner_id      varchar(64)    null,
    agent_id      varchar(64)    null,
    model         varchar(64)    null,
    input_tokens  int            null,
    output_tokens int            null,
    retries       int            null,
    repair_count  int            null,
    cost_usd      decimal(20, 8) null,
    started_at    varchar(40)    null,
    finished_at   varchar(40)    null,
    duration_ms   bigint         null,
    unit_price    decimal(41, 6) null,
    settled_abc   decimal(41, 6) null,
    constraint uq_execution_step unique (execution_id, step_id)
);

-- 실행을 닫을 때 엔진이 마지막으로 말한 것의 원본이다. 노드 출력 전문까지 들었고 프롬프트는 없다(엔진이 내지 않는다).
-- closed_by 는 무엇이 실행을 닫았는지다.
--   ENGINE       엔진이 끝(succeeded · failed)을 알렸다
--   STALLED      기한 안에 엔진이 아무 말도 하지 않아 우리가 끊었다. 원본은 마지막으로 본 것이다
--   LOST         엔진이 실행을 잊었다(재시작 · 재배포로 404). 원본은 마지막으로 본 것이다
--   UNDISPATCHED 엔진에 넘기지 못했다. 원본이 없다
-- response_sha256 은 받은 바이트의 지문이다. 꺼내 보인 원본이 저장한 그것인지 맞춰 볼 수 있다.
create table execution_run_record
(
    execution_id    varchar(36)  not null primary key,
    run_id          varchar(64)  null,
    closed_by       varchar(20)  not null,
    engine_status   varchar(20)  null,
    response        mediumtext   null,
    response_sha256 varchar(64)  null,
    note            varchar(255) null,
    recorded_at     datetime(6)  not null
);

-- 서버를 다시 띄우면 끝나지 않은 실행을 찾아 다시 따라간다(ExecutionRecoveryScheduler). 그때 상태로 고른다.
create index idx_execution_status on execution (status);
