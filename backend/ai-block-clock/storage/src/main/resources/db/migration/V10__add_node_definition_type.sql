alter table node_definition
    add column node_type varchar(20) not null default 'LLM' after name;
