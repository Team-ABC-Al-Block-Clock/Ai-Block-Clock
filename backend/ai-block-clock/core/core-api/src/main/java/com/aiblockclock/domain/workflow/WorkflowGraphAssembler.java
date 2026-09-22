package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WorkflowGraphAssembler {
    private final Map<SourceType, NodeSourceResolver> resolvers;

    public WorkflowGraphAssembler(List<NodeSourceResolver> resolvers) {
        this.resolvers = resolvers.stream()
                .collect(Collectors.toUnmodifiableMap(NodeSourceResolver::sourceType, Function.identity()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowGraph assembleWithSourceLock(List<NodePlacement> nodes, List<GraphEdge> edges) {
        WorkflowGraph graph = WorkflowGraph.of(nodes.stream().map(this::resolve).toList(), edges);
        GraphLimits.defaults().ensureWithin(graph);

        return graph;
    }

    private GraphNode resolve(NodePlacement placement) {
        return Optional.ofNullable(resolvers.get(placement.sourceType()))
                .flatMap(resolver -> resolver.resolve(placement))
                .orElseThrow(() -> new CoreException(ErrorType.WORKFLOW_NODE_SOURCE_NOT_FOUND,
                        Map.of("nodeKey", placement.key().value(), "sourceId", placement.sourceId())));
    }
}
