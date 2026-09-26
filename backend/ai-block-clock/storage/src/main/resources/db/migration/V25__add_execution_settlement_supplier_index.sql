-- 정산 내역 · 수익 통계(PAY-013/014)는 늘 "이 공급자의 것을 기간 안에서 최신순으로" 읽는다.
-- V24 와 다른 MR 이라 V24 가 먼저 배포될 수 있어, V24 를 고치지 않고 번호를 새로 받는다.
create index idx_execution_settlement_supplier_created
    on execution_settlement (supplier_id, created_at);
