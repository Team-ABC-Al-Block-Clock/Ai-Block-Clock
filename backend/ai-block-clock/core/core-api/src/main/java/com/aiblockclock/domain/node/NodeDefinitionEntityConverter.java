package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.Timestamps;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.node.NodeDefinitionEntity;

public final class NodeDefinitionEntityConverter {
    private NodeDefinitionEntityConverter() {
    }

    public static NodeDefinitionEntity toEntity(NodeDefinition node) {
        return new NodeDefinitionEntity(
                node.getId().value(),
                node.getOwner().value(),
                node.getSpec().name(),
                node.getSpec().nodeType(),
                node.getPrompt().value(),
                node.getSpec().model(),
                node.getSpec().baseCost().value(),
                node.getSpec().output().kind(),
                node.getSpec().output().example());
    }

    public static NodeDefinition toDomain(NodeDefinitionEntity entity) {
        return NodeDefinition.restore(
                NodeDefinitionId.of(entity.getId()),
                UserId.of(entity.getOwnerId()),
                toTimestamps(entity),
                toSpec(entity),
                Prompt.of(entity.getPrompt()));
    }

    public static NodeDefinitionSummary toSummary(NodeDefinitionEntity entity) {
        return new NodeDefinitionSummary(NodeDefinitionId.of(entity.getId()), toSpec(entity), toTimestamps(entity));
    }

    private static NodeSpec toSpec(NodeDefinitionEntity entity) {
        return new NodeSpec(entity.getName(), entity.getNodeType(), entity.getModel(),
                AbcAmount.of(entity.getBaseCost()), new NodeOutput(entity.getOutputKind(), entity.getOutputExample()));
    }

    private static Timestamps toTimestamps(NodeDefinitionEntity entity) {
        return new Timestamps(entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
