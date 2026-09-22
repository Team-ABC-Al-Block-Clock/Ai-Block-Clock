create table graph
(
    id varchar(36) not null,
    primary key (id)
);

create table graph_node
(
    id           bigint      not null auto_increment,
    graph_id     varchar(36) not null,
    node_key     varchar(64) not null,
    source_type  varchar(10) not null,
    reference_id varchar(36) not null,
    version_id   varchar(36) null,
    input_source varchar(20) not null,
    primary key (id),

    constraint fk_graph_node_graph
        foreign key (graph_id) references graph (id)
);

create unique index uk_graph_node_key on graph_node (graph_id, node_key);
create index idx_graph_node_reference on graph_node (reference_id);

create table graph_edge
(
    id              bigint      not null auto_increment,
    graph_id        varchar(36) not null,
    edge_key        varchar(64) not null,
    source_node_key varchar(64) not null,
    target_node_key varchar(64) not null,
    primary key (id),

    constraint fk_graph_edge_graph
        foreign key (graph_id) references graph (id)
);

create unique index uk_graph_edge_key on graph_edge (graph_id, edge_key);

create table workflow
(
    id                varchar(36)  not null,
    owner_id          bigint       not null,
    graph_id          varchar(36)  not null,
    name              varchar(40)  not null,
    description       varchar(255) null,
    validation_status varchar(20)  not null,
    revision          bigint       not null,
    last_executed_at  datetime(6)  null,
    created_at        datetime(6)  not null,
    updated_at        datetime(6)  not null,
    primary key (id),

    constraint fk_workflow_graph
        foreign key (graph_id) references graph (id)
);

create index idx_workflow_owner_last_executed on workflow (owner_id, last_executed_at, created_at, id);
create index idx_workflow_owner_created on workflow (owner_id, created_at, id);
