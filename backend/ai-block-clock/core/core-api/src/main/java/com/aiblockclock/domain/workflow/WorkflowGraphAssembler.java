package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
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

    // 가리킨 원본은 for share 로 잠그고, 실어 보낸 노드는 여기서 node_definition 에 만든다.
    // 둘 다 호출한 쪽 트랜잭션에 묶여야 하므로 MANDATORY 다.
    @Transactional(propagation = Propagation.MANDATORY)
    public WorkflowGraph lockOrCreateSourcesAndAssemble(
            UserId requester, List<NodePlacement> nodes, List<GraphEdge> edges) {
        WorkflowGraph graph =
                WorkflowGraph.of(nodes.stream().map(node -> resolve(requester, node)).toList(), edges);
        GraphLimits.defaults().ensureWithin(graph);

        return graph;
    }

    private GraphNode resolve(UserId requester, NodePlacement placement) {
        return Optional.ofNullable(resolvers.get(placement.sourceType()))
                .flatMap(resolver -> resolver.resolve(requester, placement))
                .orElseThrow(() -> new CoreException(ErrorType.WORKFLOW_NODE_SOURCE_NOT_FOUND,
                        Map.of("nodeKey", placement.key().value(), "sourceId", placement.sourceId())));
    }
}
