-- 판매자가 노드 하나를 엔진에 실제로 돌려 보는 테스트 실행(엔진 계약 v2.0 mode=test)이다.
-- 결과물을 확인하고, 엔진이 잰 달러 원가로 노드 원가를 정한다 (NOD-007). 사용자 돈이 움직이지 않아 장부와 이어지지 않는다.
-- cost_usd 는 엔진이 잰 그대로다. 노드 1회가 0.001 달러대라 소수 여섯째 자리까지 둔다 (계약 8.1).
-- result_json 은 성공한 테스트를 닫을 때 한 번 받아 JSON 으로 읽어 본 결과물(엔진의 봉투)이다. 다시 볼 때 엔진을 부르지 않는다.
-- 하루 횟수를 셀 때 (node_id, requested_at) 로 찾고, 원가를 정할 때 같은 노드의 최근 성공 기록을 찾는다.
create table node_test_run
(
    id           varchar(36)    not null,
    node_id      varchar(36)    not null,
    requester_id bigint         not null,
    run_id       varchar(64)    null,
    mock         boolean        not null,
    status       varchar(20)    not null,
    cost_usd     decimal(20, 6) null,
    failure_code varchar(64)    null,
    result_json  text           null,
    requested_at datetime(6)    not null,
    finished_at  datetime(6)    null,
    primary key (id)
);

create index idx_node_test_run_node_requested on node_test_run (node_id, requested_at);
