alter table execution
    add column input_file_key varchar(128) null;

alter table execution
    add column input_file_name varchar(255) null;

alter table execution
    add column input_file_size bigint null;
