package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.WorkflowGraph;

public final class AgentGraphFixtures {
    private AgentGraphFixtures() {
    }

    public static WorkflowGraph withAgent(AgentId agentId, AgentVersionId versionId) {
        return GraphFixtures.withAgent(agentId.value(), versionId.value());
    }
}
