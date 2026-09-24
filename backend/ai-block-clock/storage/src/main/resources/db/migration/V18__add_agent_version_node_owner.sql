alter table agent_version_node
    add column owner_id bigint null after node_key;

update agent_version_node
   set owner_id = (select agent.owner_id
                     from agent_version
                     join agent on agent.id = agent_version.agent_id
                    where agent_version.id = agent_version_node.agent_version_id)
 where owner_id is null;

alter table agent_version_node
    modify column owner_id bigint not null;
