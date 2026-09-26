-- 시연용 등급 재료 시드다. Flyway 가 읽지 않는 자리(db/seed)라 저절로 돌지 않고, 시연을 준비할 때 손으로 한 번 넣는다.
-- 값은 AI ai/demo/brand/seed_values.json 의 ag_brand_01 이다. 수식 v1.0 으로 계산하면 Tier 2 (Score 약 71.0) 가 나온다.
-- 나머지 시연 에이전트(ag_brand_02 ~ 04)는 넣지 않는다. 줄이 없으면 신규로 읽혀 Tier 4 에 머문다.
--
-- 넣는 법
--   1. 판매자 계정으로 시연 에이전트를 등록하고, ag_brand_01 자리에 등록된 에이전트의 ID 를 확인한다.
--   2. 아래 <AGENT_ID> 를 그 ID 로 바꿔 서비스 DB 에서 한 번 실행한다.
-- 다시 넣으면 재료를 시드 값으로 되돌린다. 그 사이 실제 실행으로 쌓인 성공 · 실패 수는 사라진다.
INSERT INTO agent_grade_metric (agent_id, successful_runs, supplier_failures, review_count, average_rating, measured_at)
VALUES ('<AGENT_ID>', 120, 5, 25, 4.60, CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    successful_runs = 120,
    supplier_failures = 5,
    review_count = 25,
    average_rating = 4.60,
    measured_at = CURRENT_TIMESTAMP(6);
