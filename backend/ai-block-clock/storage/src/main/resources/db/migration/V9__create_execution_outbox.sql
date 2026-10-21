-- 실행 접수를 브로커로 넘기기 전에 적어 두는 outbox 다(ADR-0001). 접수(실행 · 선차감)와 같은 트랜잭션에 한 줄 적고,
-- 디스패처가 아직 넘기지 않은 줄을 abc.runs 로 발행한다. 실행 행에는 SubmitRun 을 다시 만들 재료(입력 · 코멘트 ·
-- 런타임 워크플로우)가 남지 않아 따로 둔다.
--   payload        보낼 SubmitRun 의 JSON 본문 그대로다(ai/docs/integration-guide.md §4.3)
--   created_at     접수한 시각. 이만큼 지나도록 넘기지 못하면 디스패처가 실행을 버린다(execution.dispatch.abandon-after)
--   dispatched_at  브로커 confirm 을 받아 넘긴 시각. 넘기기 전에는 비어 있다. 감시가 큐 대기 한도를 잴 때 쓴다
create table execution_outbox
(
    execution_id  varchar(36) not null primary key,
    payload       mediumtext  not null,
    created_at    datetime(6) not null,
    dispatched_at datetime(6) null
);
