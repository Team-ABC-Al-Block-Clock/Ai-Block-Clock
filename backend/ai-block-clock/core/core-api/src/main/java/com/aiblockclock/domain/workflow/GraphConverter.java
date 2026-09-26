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
        return nodes.stream().map(node -> toNodeEntity(graphId, node)).toList();
    }

    private static GraphNodeEntity toNodeEntity(GraphId graphId, GraphNode node) {
        ControlNode control = node instanceof ControlNode placed ? placed : null;
        IoNode io = node instanceof IoNode placed ? placed : null;

        return new GraphNodeEntity(
                graphId.value(),
                node.key().value(),
                node.sourceType(),
                node.reference().referenceId(),
                node.reference().versionId(),
                node.inputSource(),
                control == null ? null : control.controlType(),
                control == null ? null : MAPPER.writeValueAsString(control.condition()),
                io == null ? null : io.ioType(),
                io == null || io.fields().isEmpty() ? null : MAPPER.writeValueAsString(io.fields()),
                io == null ? null : io.resultShape(),
                io == null ? null : io.resultFormat());
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
            case IO -> new IoNode(key, node.getIoType(), toFields(node.getIoFields()),
                    node.getResultShape(), node.getResultFormat());
        };
    }

    private static List<InputField> toFields(String json) {
        return json == null ? List.of() : List.of(MAPPER.readValue(json, InputField[].class));
    }

    private static GraphEdge toEdge(GraphEdgeEntity edge) {
        return new GraphEdge(
                EdgeKey.of(edge.getEdgeKey()),
                NodeKey.of(edge.getSourceNodeKey()),
                NodeKey.of(edge.getTargetNodeKey()),
                edge.getBranchWhen());
    }
}
