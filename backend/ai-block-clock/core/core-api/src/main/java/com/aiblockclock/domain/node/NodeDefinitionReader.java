package com.aiblockclock.domain.node;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.node.NodeDefinitionEntity;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.support.OffsetPageable;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.paging.OffsetCursor;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NodeDefinitionReader {
    private final NodeDefinitionRepository nodeRepository;

    public NodeDefinition read(NodeDefinitionId id) {
        return NodeDefinitionEntityConverter.toDomain(entity(id));
    }

    @Transactional
    public List<NodeDefinition> readAllForShare(List<NodeDefinitionId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return nodeRepository.findAllByIdForShare(ids.stream().map(NodeDefinitionId::value).toList()).stream()
                .map(NodeDefinitionEntityConverter::toDomain)
                .toList();
    }

    public boolean isOwnedBy(NodeDefinitionId id, UserId owner) {
        return nodeRepository.existsByIdAndOwnerId(id.value(), owner.value());
    }

    public CursorPage<NodeDefinitionSummary> readMine(UserId owner, String keyword, String cursor, int size) {
        int offset = OffsetCursor.decode(cursor);
        Pageable pageable = OffsetPageable.of(offset, size + 1);

        List<NodeDefinitionEntity> found = nodeRepository.findMineOrderByLatest(owner.value(), keyword, pageable);

        boolean hasNext = found.size() > size;
        List<NodeDefinitionSummary> content = found.stream()
                .limit(size)
                .map(NodeDefinitionEntityConverter::toSummary)
                .toList();

        return new CursorPage<>(content, hasNext ? OffsetCursor.encode(offset + size) : null, hasNext);
    }

    public List<NodeDefinitionSummary> readAllMine(UserId owner, String keyword) {
        return nodeRepository.findMineOrderByLatest(owner.value(), keyword, Pageable.unpaged()).stream()
                .map(NodeDefinitionEntityConverter::toSummary)
                .toList();
    }

    private NodeDefinitionEntity entity(NodeDefinitionId id) {
        return nodeRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.NODE_NOT_FOUND));
    }
}
