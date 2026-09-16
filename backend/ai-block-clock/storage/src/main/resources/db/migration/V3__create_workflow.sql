create table workflow
(
    id                varchar(36)  not null,
    owner_id          bigint       not null,
    name              varchar(40)  not null,
    description       varchar(255) null,
    validation_status varchar(20)  not null,
    revision          bigint       not null,
    last_executed_at  datetime(6)  null,
    created_at        datetime(6)  not null,
    updated_at        datetime(6)  not null,
    primary key (id)
);

create index idx_workflow_owner_last_executed on workflow (owner_id, last_executed_at, created_at, id);
create index idx_workflow_owner_created on workflow (owner_id, created_at, id);

create table workflow_node
(
    id           bigint      not null auto_increment,
    workflow_id  varchar(36) not null,
    node_key     varchar(64) not null,
    source_type  varchar(10) not null,
    reference_id varchar(36) not null,
    version_id   varchar(36) null,
    primary key (id),
    constraint fk_workflow_node_workflow foreign key (workflow_id) references workflow (id)
);

create unique index uk_workflow_node_key on workflow_node (workflow_id, node_key);

create table workflow_edge
(
    id              bigint      not null auto_increment,
    workflow_id     varchar(36) not null,
    edge_key        varchar(64) not null,
    source_node_key varchar(64) not null,
    target_node_key varchar(64) not null,
    primary key (id),
    constraint fk_workflow_edge_workflow foreign key (workflow_id) references workflow (id)
);

create unique index uk_workflow_edge_key on workflow_edge (workflow_id, edge_key);
