create table node_ref_file
(
    id                 varchar(36)  not null,
    node_definition_id varchar(36)  not null,
    ordinal            int          not null,
    file_key           varchar(255) not null,
    filename           varchar(255) not null,
    file_size          bigint       not null,
    created_at         datetime(6)  not null,
    primary key (id)
);

create unique index uk_node_ref_file_order on node_ref_file (node_definition_id, ordinal);

create table agent_version_node_ref_file
(
    id         varchar(36)  not null,
    version_id varchar(36)  not null,
    node_key   varchar(64)  not null,
    ordinal    int          not null,
    file_key   varchar(255) not null,
    filename   varchar(255) not null,
    file_size  bigint       not null,
    created_at datetime(6)  not null,
    primary key (id)
);

create unique index uk_agent_version_node_ref_file_order
    on agent_version_node_ref_file (version_id, node_key, ordinal);
