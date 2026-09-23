package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeSpec;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.enums.workflow.SourceType;

import java.util.List;
import java.util.Map;

public record VersionNode(NodeKey key, NodeSpec spec, Prompt prompt) {
    public static List<VersionNode> freeze(
            WorkflowGraph snapshot, Map<NodeDefinitionId, NodeDefinition> placedNodes) {
        return snapshot.nodesInOrder().stream()
                .filter(node -> node.sourceType() == SourceType.NODE)
                .map(node -> freeze(node, placedNodes))
                .toList();
    }

    private static VersionNode freeze(GraphNode placed, Map<NodeDefinitionId, NodeDefinition> placedNodes) {
        NodeDefinition node = placedNodes.get(NodeDefinitionId.of(placed.reference().referenceId()));

        return new VersionNode(placed.key(), node.getSpec(), node.getPrompt());
    }
}
