package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.storage.node.NodeDefinitionEntity;

public final class NodeDefinitionEntityConverter {
    private NodeDefinitionEntityConverter() {
    }

    public static NodeDefinitionEntity toEntity(NodeDefinition node) {
        return new NodeDefinitionEntity(
                node.getId().value(),
                node.getOwner().value(),
                node.getName(),
                node.getPrompt().value(),
                node.getModel(),
                node.getBaseCost().value());
    }

    public static NodeDefinition toDomain(NodeDefinitionEntity entity) {
        return NodeDefinition.create(
                NodeDefinitionId.of(entity.getId()),
                OwnerId.of(entity.getOwnerId()),
                entity.getName(),
                Prompt.of(entity.getPrompt()),
                entity.getModel(),
                AbcAmount.of(entity.getBaseCost()));
    }

    public static NodeDefinitionSummary toSummary(NodeDefinitionEntity entity) {
        return new NodeDefinitionSummary(
                NodeDefinitionId.of(entity.getId()),
                entity.getName(),
                entity.getModel(),
                AbcAmount.of(entity.getBaseCost()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
