create table execution_result
(
    id           bigint       not null auto_increment,
    execution_id varchar(36)  not null,
    format       varchar(20)  not null,
    file_key     varchar(128) not null,
    file_size    bigint       not null,
    primary key (id)
);

create unique index uk_execution_result_format on execution_result (execution_id, format);
