create table execution
(
    id           varchar(36) not null,
    workflow_id  varchar(36) not null,
    requester_id bigint      not null,
    run_id       varchar(64) not null,
    status       varchar(20) not null,
    prompt_count int         not null,
    created_at   datetime(6) not null,
    updated_at   datetime(6) not null,
    primary key (id)
);

create index idx_execution_requester_created on execution (requester_id, created_at, id);
create index idx_execution_workflow on execution (workflow_id);
