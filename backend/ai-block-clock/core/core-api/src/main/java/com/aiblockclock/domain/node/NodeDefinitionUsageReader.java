package com.aiblockclock.domain.node;

import com.aiblockclock.storage.graph.GraphNodeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NodeDefinitionUsageReader {
    private final GraphNodeRepository graphNodeRepository;

    public boolean isPlacedInAnyGraph(NodeDefinitionId nodeId) {
        return graphNodeRepository.existsByReferenceId(nodeId.value());
    }
}
