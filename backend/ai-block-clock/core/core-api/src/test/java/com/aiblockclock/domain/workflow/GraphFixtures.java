package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.agent.AgentVersionId;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GraphFixtures {
    private GraphFixtures() {
    }

    public static GraphNode node(String key) {
        return new BaseNode(NodeKey.of(key), deterministicId(key));
    }

    public static AgentNode agent(String key, UUID agentId, UUID versionId) {
        return new AgentNode(NodeKey.of(key), agentId, versionId);
    }

    public static AgentNode agent(String key, AgentId agentId, AgentVersionId versionId) {
        return agent(key, agentId.value(), versionId.value());
    }

    public static GraphEdge edge(String key, String source, String target) {
        return new GraphEdge(EdgeKey.of(key), NodeKey.of(source), NodeKey.of(target));
    }

    public static WorkflowGraph straightLine() {
        return chainOf(3);
    }

    public static WorkflowGraph chainOf(int length) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        for (int i = 1; i <= length; i++) {
            nodes.add(node("n" + i));
            if (i > 1) {
                edges.add(edge("e" + i, "n" + (i - 1), "n" + i));
            }
        }

        return WorkflowGraph.of(nodes, edges);
    }

    public static WorkflowGraph withAgent(UUID agentId, UUID versionId) {
        return WorkflowGraph.of(
                List.of(node("n1"), agent("n2", agentId, versionId), node("n3")),
                List.of(edge("e1", "n1", "n2"), edge("e2", "n2", "n3")));
    }

    public static WorkflowGraph withAgent(AgentId agentId, AgentVersionId versionId) {
        return withAgent(agentId.value(), versionId.value());
    }

    private static UUID deterministicId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
