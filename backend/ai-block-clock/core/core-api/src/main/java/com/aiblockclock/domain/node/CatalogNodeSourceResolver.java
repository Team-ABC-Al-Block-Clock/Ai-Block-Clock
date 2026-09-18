package com.aiblockclock.domain.node;

import com.aiblockclock.domain.workflow.BaseNode;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.NodeSourceResolver;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.node.NodeDefinitionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CatalogNodeSourceResolver implements NodeSourceResolver {
    private final NodeDefinitionRepository nodeRepository;

    @Override
    public SourceType sourceType() {
        return SourceType.NODE;
    }

    @Override
    public Optional<GraphNode> resolve(NodePlacement placement) {
        if (!nodeRepository.existsById(placement.sourceId())) {
            return Optional.empty();
        }

        return Optional.of(new BaseNode(placement.key(), placement.sourceId(), placement.inputSource()));
    }
}
