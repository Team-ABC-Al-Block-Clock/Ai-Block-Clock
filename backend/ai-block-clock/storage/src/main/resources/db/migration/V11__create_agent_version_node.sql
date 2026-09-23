create table agent_version_node
(
    id               bigint         not null auto_increment,
    agent_version_id varchar(36)    not null,
    node_key         varchar(64)    not null,
    name             varchar(40)    not null,
    node_type        varchar(20)    not null,
    prompt           text           not null,
    model            varchar(40)    not null,
    base_cost        decimal(41, 6) not null,
    primary key (id),

    constraint fk_agent_version_node_version
        foreign key (agent_version_id) references agent_version (id)
);

create unique index uk_agent_version_node_key on agent_version_node (agent_version_id, node_key);
