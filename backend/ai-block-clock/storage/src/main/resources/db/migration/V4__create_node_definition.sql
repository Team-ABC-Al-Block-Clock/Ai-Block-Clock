create table node_definition
(
    id         varchar(36)    not null,
    owner_id   bigint         not null,
    name       varchar(40)    not null,
    prompt     text           not null,
    model      varchar(40)    not null,
    base_cost  decimal(41, 6) not null,
    created_at datetime(6)    not null,
    updated_at datetime(6)    not null,
    primary key (id)
);

create index idx_node_definition_owner_created on node_definition (owner_id, created_at, id);
