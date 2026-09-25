package com.aiblockclock.domain.node;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.NodeSourceDescriber;
import com.aiblockclock.domain.workflow.PlacedSource;
import com.aiblockclock.enums.workflow.SourceType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CatalogNodeSourceDescriber implements NodeSourceDescriber {
    private final NodeDefinitionReader nodeReader;

    @Override
    public SourceType sourceType() {
        return SourceType.NODE;
    }

    // 읽기만 하므로 잠그지 않는다. readAllForShare 는 저장 경로에서 원본을 붙잡는 용도라 여기 쓰면 안 된다.
    @Override
    public Map<UUID, PlacedSource> describe(UserId requester, List<NodeReference> references) {
        List<NodeDefinitionId> ids = references.stream()
                .map(NodeReference::referenceId).distinct().map(NodeDefinitionId::of).toList();

        return nodeReader.readAll(ids).stream()
                .collect(Collectors.toUnmodifiableMap(
                        node -> node.getId().value(),
                        node -> new PlacedSource(node.getSpec().name(), node.isOwnedBy(requester))));
    }
}
