package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeSpec;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.node.RefFileId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.storage.agent.AgentVersionNodeEntity;
import com.aiblockclock.storage.agent.AgentVersionNodeRefFileEntity;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public final class AgentVersionNodeEntityConverter {
    private AgentVersionNodeEntityConverter() {
    }

    public static AgentVersionNodeEntity toEntity(AgentVersionId versionId, VersionNode node) {
        NodeSpec spec = node.spec();

        return new AgentVersionNodeEntity(
                versionId.value(),
                node.key().value(),
                node.owner().value(),
                spec.name(),
                spec.nodeType(),
                node.prompt().value(),
                spec.model(),
                spec.baseCost().value());
    }

    public static List<AgentVersionNodeRefFileEntity> toRefFileEntities(
            AgentVersionId versionId, VersionNode node) {
        List<RefFile> files = node.refFiles();

        return IntStream.range(0, files.size())
                .mapToObj(ordinal -> toRefFileEntity(versionId, node.key(), ordinal, files.get(ordinal)))
                .toList();
    }

    public static VersionNode toDomain(AgentVersionNodeEntity entity, List<RefFile> refFiles) {
        return new VersionNode(
                NodeKey.of(entity.getNodeKey()),
                UserId.of(entity.getOwnerId()),
                new NodeSpec(entity.getName(), entity.getNodeType(), entity.getModel(),
                        AbcAmount.of(entity.getBaseCost())),
                Prompt.of(entity.getPrompt()),
                refFiles);
    }

    public static RefFile toRefFile(AgentVersionNodeRefFileEntity entity) {
        return new RefFile(RefFileId.of(entity.getId()), entity.getFileKey(),
                entity.getFilename(), entity.getFileSize());
    }

    private static AgentVersionNodeRefFileEntity toRefFileEntity(
            AgentVersionId versionId, NodeKey key, int ordinal, RefFile file) {
        return new AgentVersionNodeRefFileEntity(UUID.randomUUID(), versionId.value(), key.value(),
                ordinal, file.key(), file.filename(), file.size());
    }
}
