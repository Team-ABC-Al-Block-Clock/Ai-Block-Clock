package com.aiblockclock.domain.workflow;

import com.aiblockclock.storage.graph.GraphEdgeEntity;
import com.aiblockclock.storage.graph.GraphNodeEntity;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

public final class GraphConverter {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private GraphConverter() {
    }

    public static WorkflowGraph toGraph(List<GraphNodeEntity> nodeRows, List<GraphEdgeEntity> edgeRows) {
        return WorkflowGraph.of(
                nodeRows.stream().map(GraphConverter::toNode).toList(),
                edgeRows.stream().map(GraphConverter::toEdge).toList());
    }

    public static List<GraphNodeEntity> toNodeEntities(GraphId graphId, List<GraphNode> nodes) {
        return nodes.stream()
                .map(node -> new GraphNodeEntity(
                        graphId.value(),
                        node.key().value(),
                        node.sourceType(),
                        node.reference().referenceId(),
                        node.reference().versionId(),
                        node.inputSource(),
                        node instanceof ControlNode control ? control.controlType() : null,
                        node instanceof ControlNode control ? MAPPER.writeValueAsString(control.condition()) : null))
                .toList();
    }

    public static List<GraphEdgeEntity> toEdgeEntities(GraphId graphId, List<GraphEdge> edges) {
        return edges.stream()
                .map(edge -> new GraphEdgeEntity(
                        graphId.value(),
                        edge.key().value(),
                        edge.sourceNodeKey().value(),
                        edge.targetNodeKey().value(),
                        edge.when()))
                .toList();
    }

    private static GraphNode toNode(GraphNodeEntity node) {
        NodeKey key = NodeKey.of(node.getNodeKey());

        return switch (node.getSourceType()) {
            case NODE -> {
                if (node.getVersionId() != null) {
                    throw new CoreException(ErrorType.WORKFLOW_NODE_REFERENCE_MISMATCH,
                            Map.of("nodeKey", key.value(), "sourceType", node.getSourceType().name()));
                }

                yield new BaseNode(key, node.getReferenceId(), node.getInputSource());
            }
            case AGENT -> new AgentNode(key, node.getReferenceId(), node.getVersionId(), node.getInputSource());
            case CONTROL -> new ControlNode(key, node.getControlType(),
                    MAPPER.readValue(node.getBranchCondition(), BranchCondition.class));
        };
    }

    private static GraphEdge toEdge(GraphEdgeEntity edge) {
        return new GraphEdge(
                EdgeKey.of(edge.getEdgeKey()),
                NodeKey.of(edge.getSourceNodeKey()),
                NodeKey.of(edge.getTargetNodeKey()),
                edge.getBranchWhen());
    }
}
