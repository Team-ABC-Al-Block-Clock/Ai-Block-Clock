alter table workflow_node
    add column input_source varchar(20) not null default 'PREV';
