package com.aiblockclock.domain.workflow;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@Getter
@RequiredArgsConstructor
public final class GraphLimits {
    public static final int DEFAULT_MAX_NODES = 20;
    public static final int DEFAULT_MAX_DEPTH = 10;

    private static final GraphLimits DEFAULTS = new GraphLimits(DEFAULT_MAX_NODES, DEFAULT_MAX_DEPTH);

    private final int maxNodes;
    private final int maxDepth;

    public static GraphLimits defaults() {
        return DEFAULTS;
    }

    public void ensureWithin(WorkflowGraph graph) {
        if (graph.isEmpty()) {
            throw new CoreException(ErrorType.WORKFLOW_GRAPH_EMPTY);
        }

        int length = graph.chainLength();

        if (length > maxNodes) {
            throw new CoreException(ErrorType.WORKFLOW_NODE_LIMIT_EXCEEDED,
                    Map.of("limit", maxNodes, "actual", length));
        }

        if (length > maxDepth) {
            throw new CoreException(ErrorType.WORKFLOW_DEPTH_LIMIT_EXCEEDED,
                    Map.of("limit", maxDepth, "actual", length));
        }
    }
}
