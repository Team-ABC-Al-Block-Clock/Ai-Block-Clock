-- 에이전트에 종합 점수와 등급(PAY-019/020)을 매길 때마다 한 줄씩 남긴다. 한 번 적고 고치지 않는다.
-- 재료(successful_runs ~ measured_at)는 매긴 순간의 agent_grade_metric 값을 그대로 옮긴 것이다. 재료 표는 계속 바뀌므로
-- 여기에 두지 않으면 그때 왜 그 등급이었는지 다시 볼 수 없다. measured_at 은 재료가 마지막으로 바뀐 때이고, 신규면 비어 있다.
-- 지표(rating_score · usage_score · success_rate)도 매긴 순간의 수식으로 낸 값이다. 조정값이 바뀌어도 그때의 값이 남는다.
-- score_tier 는 점수만 보고 닿은 등급, tier 는 허들까지 본 확정 등급이다. 허들에 걸리면 tier 가 더 낮다.
-- 가격에 쓰는 등급(agent_version.tier)은 여기서 바꾸지 않는다. 재산정 배치(PAY-022)가 이 결과를 보고 바꾼다.
create table agent_grade_history
(
    id                bigint auto_increment primary key,
    agent_id          varchar(36)   not null,
    successful_runs   bigint        not null,
    supplier_failures bigint        not null,
    review_count      int           not null,
    average_rating    decimal(3, 2) null,
    measured_at       datetime(6)   null,
    rating_score      decimal(9, 6) not null,
    usage_score       decimal(9, 6) not null,
    success_rate      decimal(7, 6) not null,
    score             decimal(7, 4) not null,
    score_tier        varchar(20)   not null,
    tier              varchar(20)   not null,
    created_at        datetime(6)   not null,

    constraint ck_agent_grade_history_counts
        check (successful_runs >= 0 and supplier_failures >= 0 and review_count >= 0),
    constraint ck_agent_grade_history_rating
        check ((review_count = 0 and average_rating is null)
            or (review_count > 0 and average_rating is not null and average_rating between 1 and 5)),
    constraint ck_agent_grade_history_scores
        check (rating_score between 0 and 100 and usage_score between 0 and 100
            and success_rate between 0 and 1 and score between 0 and 100)
);
