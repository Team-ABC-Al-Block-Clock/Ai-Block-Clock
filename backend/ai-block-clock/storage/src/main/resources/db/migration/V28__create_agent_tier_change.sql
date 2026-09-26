-- 재산정 배치(PAY-022)가 버전의 가격 등급(agent_version.tier)을 바꿀 때마다 한 줄씩 남긴다. 한 번 적고 고치지 않는다.
-- 등급은 에이전트에 하나지만 가격 칸은 버전마다 있어, 바뀐 버전마다 한 줄이다. 바뀌지 않은 버전은 남기지 않는다.
-- before_price · after_price 는 그 버전의 원가에 바뀌기 전 · 뒤 등급의 배율을 곱한 판매가다.
-- grade_history_id 는 이 등급을 매긴 근거인 agent_grade_history 의 줄이다. 그때의 재료 · 지표 · 점수는 그 줄에 있다.
-- created_at 이 바뀐 때다. 이미 접수한 실행은 접수 때 적은 배율(execution_charge_rate)을 쓰므로 이 시점 뒤 새 실행부터 새 가격이다.
create table agent_tier_change
(
    id               bigint auto_increment primary key,
    agent_id         varchar(36)    not null,
    agent_version_id varchar(36)    not null,
    grade_history_id bigint         not null,
    before_tier      varchar(20)    not null,
    after_tier       varchar(20)    not null,
    before_price     decimal(41, 6) not null,
    after_price      decimal(41, 6) not null,
    created_at       datetime(6)    not null,

    constraint ck_agent_tier_change_changed check (before_tier <> after_tier),
    constraint ck_agent_tier_change_prices check (before_price > 0 and after_price > 0)
);

-- 배치는 에이전트마다 마지막 이력을 읽어, 같은 결과면 줄을 더하지 않는다. 그 조회가 쓰는 색인이다.
create index ix_agent_grade_history_agent on agent_grade_history (agent_id, id);
