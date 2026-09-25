alter table graph_node
    add column io_type varchar(20) null after branch_condition;

alter table graph_node
    add column io_fields text null after io_type;

alter table graph_node
    add column result_shape varchar(20) null after io_fields;

alter table graph_node
    add column result_format varchar(20) null after result_shape;
