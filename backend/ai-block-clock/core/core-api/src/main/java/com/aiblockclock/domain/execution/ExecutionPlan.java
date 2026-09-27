package com.aiblockclock.domain.execution;

import com.aiblockclock.enums.node.AiModel;

import java.util.List;

public record ExecutionPlan(List<PlannedNode> nodes, List<PlannedEdge> edges) {
    public ExecutionPlan {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public List<PlannedPrompt> prompts() {
        return nodes.stream().flatMap(PlannedNode::prompts).toList();
    }

    public int promptCount() {
        return prompts().size();
    }

    public AiModel model() {
        return prompts().get(0).model();
    }
}
