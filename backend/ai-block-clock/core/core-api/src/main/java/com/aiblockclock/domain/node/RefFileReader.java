package com.aiblockclock.domain.node;

import com.aiblockclock.storage.node.NodeRefFileEntity;
import com.aiblockclock.storage.node.NodeRefFileRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RefFileReader {
    private final NodeRefFileRepository refFileRepository;

    public List<RefFile> read(NodeDefinitionId nodeId) {
        return refFileRepository.findByNodeDefinitionIdOrderByOrdinal(nodeId.value()).stream()
                .map(RefFileEntityConverter::toDomain)
                .toList();
    }

    public Map<NodeDefinitionId, List<RefFile>> readAll(List<NodeDefinitionId> nodeIds) {
        List<UUID> values = nodeIds.stream().map(NodeDefinitionId::value).toList();

        return refFileRepository.findByNodeDefinitionIdInOrderByNodeDefinitionIdAscOrdinalAsc(values).stream()
                .collect(Collectors.groupingBy(
                        entity -> NodeDefinitionId.of(entity.getNodeDefinitionId()),
                        Collectors.mapping(RefFileEntityConverter::toDomain, Collectors.toList())));
    }
}
