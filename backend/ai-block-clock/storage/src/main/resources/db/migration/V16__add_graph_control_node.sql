alter table graph_node
    modify column reference_id varchar(36) null;

alter table graph_node
    add column control_type varchar(20) null after input_source;

alter table graph_node
    add column branch_condition text null after control_type;

alter table graph_edge
    add column branch_when boolean null after target_node_key;
