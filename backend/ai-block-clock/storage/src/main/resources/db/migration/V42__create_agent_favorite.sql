create table agent_favorite
(
    id         bigint auto_increment primary key,
    user_id    bigint      not null,
    agent_id   varchar(36) not null,
    created_at datetime(6) not null,

    constraint uq_agent_favorite_user_agent unique (user_id, agent_id)
);

create index idx_agent_favorite_user_created on agent_favorite (user_id, created_at, id);

create index idx_agent_favorite_agent on agent_favorite (agent_id);
