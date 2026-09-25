alter table execution
    modify column run_id varchar(64) null;

alter table execution
    add column request_id varchar(36) null;

-- 요청 번호가 생기기 전의 실행은 저마다 자기 ID 를 번호로 삼아 서로 겹치지 않게 한다.
update execution
   set request_id = id;

alter table execution
    modify column request_id varchar(36) not null;

alter table execution
    add constraint uq_execution_requester_request unique (requester_id, request_id);
