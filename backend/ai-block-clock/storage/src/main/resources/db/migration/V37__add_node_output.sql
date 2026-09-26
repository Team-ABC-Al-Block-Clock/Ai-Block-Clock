alter table node_definition
    add column output_kind varchar(10) not null default 'TEXT' after model;

alter table node_definition
    add column output_sample varchar(64) null after output_kind;

alter table agent_version_node
    add column output_kind varchar(10) not null default 'TEXT' after model;

alter table agent_version_node
    add column output_sample varchar(64) null after output_kind;
