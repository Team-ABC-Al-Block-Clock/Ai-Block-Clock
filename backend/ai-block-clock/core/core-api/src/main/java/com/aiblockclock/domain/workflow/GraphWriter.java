package com.aiblockclock.domain.workflow;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GraphWriter {
    private final GraphNodeWriter graphNodeWriter;
    private final GraphEdgeWriter graphEdgeWriter;

    @Transactional
    public void append(GraphId graphId, WorkflowGraph graph) {
        graphNodeWriter.append(graphId, graph.getNodes());
        graphEdgeWriter.append(graphId, graph.getEdges());
    }

    @Transactional
    public void replace(GraphId graphId, WorkflowGraph graph) {
        graphNodeWriter.replace(graphId, graph.getNodes());
        graphEdgeWriter.replace(graphId, graph.getEdges());
    }

    @Transactional
    public void removeAll(GraphId graphId) {
        graphEdgeWriter.removeAll(graphId);
        graphNodeWriter.removeAll(graphId);
    }
}
