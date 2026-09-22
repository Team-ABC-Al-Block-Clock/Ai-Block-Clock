create table agent
(
    id                 varchar(36)   not null,
    owner_id           bigint        not null,
    name               varchar(40)   not null,
    summary            varchar(80)   not null,
    description        varchar(2000) not null,
    usage_example      varchar(500)  null,
    onboarding_message varchar(20)   null,
    thumbnail_url      varchar(500)  null,
    public_version_id  varchar(36)   null,
    created_at         datetime(6)   not null,
    updated_at         datetime(6)   not null,
    primary key (id)
);

create index idx_agent_owner_created on agent (owner_id, created_at, id);

create table agent_version
(
    id                 varchar(36)    not null,
    agent_id           varchar(36)    not null,
    version_no         int            not null,
    graph_id           varchar(36)    not null,
    source_workflow_id varchar(36)    not null,
    source_revision    bigint         not null,
    prompt_node_count  int            not null,
    base_cost          decimal(41, 6) not null,
    tier               varchar(20)    not null,
    publication_state  varchar(20)    not null,
    created_at         datetime(6)    not null,
    updated_at         datetime(6)    not null,
    primary key (id),

    constraint fk_agent_version_agent
        foreign key (agent_id) references agent (id)
);

create unique index uk_agent_version_no on agent_version (agent_id, version_no);
create index idx_agent_version_source on agent_version (source_workflow_id);
