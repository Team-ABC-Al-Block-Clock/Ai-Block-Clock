package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.WorkflowGraph;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public record AgentPlacement(AgentId agentId, AgentVersionId versionId) {
    public static AgentPlacement from(NodeReference reference) {
        return new AgentPlacement(AgentId.of(reference.referenceId()), AgentVersionId.of(reference.versionId()));
    }

    public static Set<AgentPlacement> in(WorkflowGraph graph) {
        return graph.agentReferences().stream()
                .map(AgentPlacement::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public NodeReference toNodeReference() {
        return NodeReference.of(agentId.value(), versionId.value());
    }
}
