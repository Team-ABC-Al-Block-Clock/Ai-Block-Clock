package com.aiblockclock.domain.node;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.agent.NodeDefinitionOwnershipView;
import com.aiblockclock.storage.node.NodeDefinitionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NodeDefinitionOwnershipReader implements NodeDefinitionOwnershipView {
    private final NodeDefinitionRepository nodeRepository;

    @Override
    public boolean isOwnedBy(NodeDefinitionId nodeId, OwnerId owner) {
        return nodeRepository.existsByIdAndOwnerId(nodeId.value(), owner.value());
    }
}
