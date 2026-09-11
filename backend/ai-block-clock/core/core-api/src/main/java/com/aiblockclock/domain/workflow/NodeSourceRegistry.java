package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NodeSourceRegistry {
    private final Map<SourceType, NodeSourceResolver> resolvers;

    public NodeSourceRegistry(List<NodeSourceResolver> resolvers) {
        this.resolvers = resolvers.stream()
                .collect(Collectors.toUnmodifiableMap(NodeSourceResolver::sourceType, Function.identity()));
    }

    public Optional<GraphNode> resolve(NodePlacement placement) {
        return Optional.ofNullable(resolvers.get(placement.sourceType()))
                .flatMap(resolver -> resolver.resolve(placement));
    }
}
