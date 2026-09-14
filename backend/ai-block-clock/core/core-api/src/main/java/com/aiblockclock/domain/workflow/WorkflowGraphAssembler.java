package com.aiblockclock.domain.workflow;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkflowGraphAssembler {
    private final NodeSourceRegistry nodeSourceRegistry;

    public WorkflowGraph assemble(List<NodePlacement> nodes, List<GraphEdge> edges) {
        WorkflowGraph graph = WorkflowGraph.of(nodes.stream().map(this::resolve).toList(), edges);
        GraphLimits.defaults().ensureWithin(graph);
        return graph;
    }

    private GraphNode resolve(NodePlacement placement) {
        return nodeSourceRegistry.resolve(placement)
                .orElseThrow(() -> new CoreException(ErrorType.WORKFLOW_NODE_SOURCE_NOT_FOUND,
                        Map.of("nodeKey", placement.key().value(), "sourceId", placement.sourceId())));
    }
}
