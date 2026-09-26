-- 에이전트 등급(PAY-016 ~ 018)을 계산할 재료다. 에이전트마다 한 줄이고, 줄이 없는 에이전트는 아직 세지 않은 신규다.
-- successful_runs · supplier_failures 는 실행을 닫을 때 더한다. 자기 실행 · 플랫폼 탓 · mock 성공은 세지 않는다.
-- review_count · average_rating 은 리뷰 기능이 아직 없어 시연 시드(db/seed/agent-grade-demo.sql)로만 들어온다.
-- 지표(보정 평점 · 정규화 사용량 · 성공률)는 저장하지 않는다. 수식의 조정값이 바뀌어도 낡은 값이 남지 않게 이 재료에서 매번 계산한다.
-- measured_at 은 재료가 마지막으로 바뀐 때다. 지표는 이 시점의 값이다.
-- CHECK 는 결과가 NULL 이면 통과시키므로, 평가가 있는데 평점이 빈 줄을 막으려고 is not null 을 따로 적는다.
create table agent_grade_metric
(
    agent_id          varchar(36)   not null,
    successful_runs   bigint        not null,
    supplier_failures bigint        not null,
    review_count      int           not null,
    average_rating    decimal(3, 2) null,
    measured_at       datetime(6)   not null,

    primary key (agent_id),
    constraint ck_agent_grade_metric_counts
        check (successful_runs >= 0 and supplier_failures >= 0 and review_count >= 0),
    constraint ck_agent_grade_metric_rating
        check ((review_count = 0 and average_rating is null)
            or (review_count > 0 and average_rating is not null and average_rating between 1 and 5))
);
