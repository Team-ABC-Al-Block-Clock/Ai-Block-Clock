package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.Timestamps;
import com.aiblockclock.storage.node.NodeDefinitionEntity;

public final class NodeDefinitionEntityConverter {
    private NodeDefinitionEntityConverter() {
    }

    public static NodeDefinitionEntity toEntity(NodeDefinition node) {
        return new NodeDefinitionEntity(
                node.getId().value(),
                node.getOwner().value(),
                node.getSpec().name(),
                node.getPrompt().value(),
                node.getSpec().model(),
                node.getSpec().baseCost().value());
    }

    public static NodeDefinition toDomain(NodeDefinitionEntity entity) {
        return NodeDefinition.restore(
                NodeDefinitionId.of(entity.getId()),
                OwnerId.of(entity.getOwnerId()),
                toTimestamps(entity),
                toSpec(entity),
                Prompt.of(entity.getPrompt()));
    }

    public static NodeDefinitionSummary toSummary(NodeDefinitionEntity entity) {
        return new NodeDefinitionSummary(NodeDefinitionId.of(entity.getId()), toSpec(entity), toTimestamps(entity));
    }

    private static NodeSpec toSpec(NodeDefinitionEntity entity) {
        return new NodeSpec(entity.getName(), entity.getModel(), AbcAmount.of(entity.getBaseCost()));
    }

    private static Timestamps toTimestamps(NodeDefinitionEntity entity) {
        return new Timestamps(entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
