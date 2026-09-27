package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.node.RefFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record PlannedAgent(String id, AgentId agent, List<PlannedNode> nodes, List<PlannedEdge> edges)
        implements PlannedNode {
    public PlannedAgent {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    @Override
    public Stream<PlannedPrompt> prompts() {
        return nodes.stream().flatMap(PlannedNode::prompts);
    }

    @Override
    public Map<String, Object> toRuntime(Map<String, List<RefFile>> refFiles) {
        return RuntimeWorkflow.agent(this, refFiles);
    }
}
