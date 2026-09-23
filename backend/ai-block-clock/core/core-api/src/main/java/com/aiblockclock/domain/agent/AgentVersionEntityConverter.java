package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.workflow.GraphId;
import com.aiblockclock.storage.agent.AgentEntity;
import com.aiblockclock.storage.agent.AgentVersionEntity;

public final class AgentVersionEntityConverter {
    private AgentVersionEntityConverter() {
    }

    public static AgentVersionEntity toEntity(AgentVersion version, AgentEntity agent, GraphId graphId) {
        SourceWorkflow source = version.getSource();
        VersionCost cost = version.getCost();

        return new AgentVersionEntity(
                version.getId().value(),
                agent,
                version.getNumber().value(),
                graphId.value(),
                source.workflowId().value(),
                source.revision().value(),
                cost.promptNodeCount(),
                cost.baseCost().value(),
                version.getTier(),
                version.getState());
    }
}
