package com.aiblockclock.domain.node;

import com.aiblockclock.storage.node.NodeDefinitionEntity;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NodeDefinitionWriter {
    private final NodeDefinitionRepository nodeRepository;

    @Transactional
    public NodeDefinition append(NodeDefinition node) {
        return NodeDefinitionEntityConverter.toDomain(nodeRepository.save(NodeDefinitionEntityConverter.toEntity(node)));
    }

    @Transactional
    public NodeDefinition update(NodeDefinition node) {
        NodeDefinitionEntity entity = nodeRepository.findById(node.getId().value())
                .orElseThrow(() -> new CoreException(ErrorType.NODE_NOT_FOUND));

        entity.edit(node.getName(), node.getPrompt().value(), node.getModel(), node.getBaseCost().value());

        return NodeDefinitionEntityConverter.toDomain(entity);
    }

    @Transactional
    public void remove(NodeDefinitionId id) {
        nodeRepository.deleteById(id.value());
    }
}
