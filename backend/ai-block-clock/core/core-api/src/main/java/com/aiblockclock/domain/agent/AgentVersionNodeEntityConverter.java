package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeSpec;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.storage.agent.AgentVersionNodeEntity;

public final class AgentVersionNodeEntityConverter {
    private AgentVersionNodeEntityConverter() {
    }

    public static AgentVersionNodeEntity toEntity(AgentVersionId versionId, VersionNode node) {
        NodeSpec spec = node.spec();

        return new AgentVersionNodeEntity(
                versionId.value(),
                node.key().value(),
                spec.name(),
                spec.nodeType(),
                node.prompt().value(),
                spec.model(),
                spec.baseCost().value());
    }

    public static VersionNode toDomain(AgentVersionNodeEntity entity) {
        return new VersionNode(
                NodeKey.of(entity.getNodeKey()),
                new NodeSpec(entity.getName(), entity.getNodeType(), entity.getModel(),
                        AbcAmount.of(entity.getBaseCost())),
                Prompt.of(entity.getPrompt()));
    }
}
