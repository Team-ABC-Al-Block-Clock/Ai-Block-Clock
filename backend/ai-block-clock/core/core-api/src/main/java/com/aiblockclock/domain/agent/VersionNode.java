package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeSpec;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.enums.workflow.SourceType;

import java.util.List;
import java.util.Map;

public record VersionNode(
        NodeKey key, UserId owner, NodeSpec spec, Prompt prompt, List<RefFile> refFiles) {
    public VersionNode {
        refFiles = List.copyOf(refFiles);
    }

    public static List<VersionNode> freeze(WorkflowGraph snapshot,
                                           Map<NodeDefinitionId, NodeDefinition> placedNodes,
                                           Map<NodeDefinitionId, List<RefFile>> placedRefFiles) {
        return snapshot.nodesInOrder().stream()
                .filter(node -> node.sourceType() == SourceType.NODE)
                .map(node -> freeze(node, placedNodes, placedRefFiles))
                .toList();
    }

    private static VersionNode freeze(GraphNode placed,
                                      Map<NodeDefinitionId, NodeDefinition> placedNodes,
                                      Map<NodeDefinitionId, List<RefFile>> placedRefFiles) {
        NodeDefinitionId nodeId = NodeDefinitionId.of(placed.reference().referenceId());
        NodeDefinition node = placedNodes.get(nodeId);

        return new VersionNode(placed.key(), node.getOwner(), node.getSpec(), node.getPrompt(),
                placedRefFiles.getOrDefault(nodeId, List.of()));
    }
}
