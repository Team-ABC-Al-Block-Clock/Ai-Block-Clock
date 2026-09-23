package com.aiblockclock.domain.node;

import com.aiblockclock.storage.node.NodeRefFileEntity;

public final class RefFileEntityConverter {
    private RefFileEntityConverter() {
    }

    public static NodeRefFileEntity toEntity(NodeDefinitionId nodeId, int ordinal, RefFile file) {
        return new NodeRefFileEntity(
                file.id().value(), nodeId.value(), ordinal, file.key(), file.filename(), file.size());
    }

    public static RefFile toDomain(NodeRefFileEntity entity) {
        return new RefFile(RefFileId.of(entity.getId()), entity.getFileKey(),
                entity.getFilename(), entity.getFileSize());
    }
}
