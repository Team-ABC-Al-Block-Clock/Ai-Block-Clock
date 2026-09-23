package com.aiblockclock.domain.node;

import com.aiblockclock.storage.node.NodeRefFileEntity;
import com.aiblockclock.storage.node.NodeRefFileRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RefFileWriter {
    private final NodeRefFileRepository refFileRepository;

    @Transactional
    public RefFile append(NodeDefinitionId nodeId, RefFile file) {
        return RefFileEntityConverter.toDomain(
                refFileRepository.save(RefFileEntityConverter.toEntity(nodeId, nextOrdinal(nodeId), file)));
    }

    @Transactional
    public void remove(NodeDefinitionId nodeId, RefFileId fileId) {
        NodeRefFileEntity target =
                refFileRepository.findByNodeDefinitionIdOrderByOrdinal(nodeId.value()).stream()
                        .filter(entity -> entity.getId().equals(fileId.value()))
                        .findFirst()
                        .orElseThrow(() -> new CoreException(ErrorType.REF_FILE_NOT_FOUND));

        refFileRepository.delete(target);
    }

    @Transactional
    public void removeAll(NodeDefinitionId nodeId) {
        refFileRepository.deleteAllByNodeDefinitionId(nodeId.value());
    }

    private int nextOrdinal(NodeDefinitionId nodeId) {
        List<NodeRefFileEntity> attached =
                refFileRepository.findByNodeDefinitionIdOrderByOrdinal(nodeId.value());

        return attached.stream()
                .mapToInt(NodeRefFileEntity::getOrdinal)
                .max()
                .orElse(-1) + 1;
    }
}
