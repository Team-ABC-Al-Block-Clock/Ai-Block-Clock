-- 실패한 실행이 누구 탓인지다. 끝날 때 한 번 적고 고치지 않는다. 성공한 실행과 이 칸보다 먼저 끝난 실행은 비어 있다.
-- 책임 공급자 · 에이전트는 공급자 탓일 때만 적는다. 성공률 집계(PAY-018)와 정산(PAY-012)이 읽는다.
alter table execution
    add column failed_step_id varchar(16) null;

alter table execution
    add column failure_code varchar(64) null;

alter table execution
    add column failure_fault varchar(20) null;

alter table execution
    add column failure_affects_success_rate boolean null;

alter table execution
    add column fault_supplier_id bigint null;

alter table execution
    add column fault_agent_id varchar(36) null;
