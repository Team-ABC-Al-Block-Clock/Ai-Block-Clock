package com.aiblockclock.domain.node;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.NodeSourceDescriber;
import com.aiblockclock.domain.workflow.PlacedSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.node.NodeDefinitionEntity;
import com.aiblockclock.storage.node.NodeDefinitionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogNodeSourceDescriber implements NodeSourceDescriber {
    private final NodeDefinitionRepository nodeRepository;

    @Override
    public SourceType sourceType() {
        return SourceType.NODE;
    }

    // 읽기만 하므로 잠그지 않는다. readAllForShare 는 저장 경로에서 원본을 붙잡는 용도라 여기 쓰면 안 된다.
    @Override
    public Map<UUID, PlacedSource> describe(UserId requester, List<NodeReference> references) {
        List<UUID> ids = references.stream().map(NodeReference::referenceId).distinct().toList();

        if (ids.isEmpty()) {
            return Map.of();
        }

        return nodeRepository.findAllById(ids).stream()
                .collect(Collectors.toUnmodifiableMap(
                        NodeDefinitionEntity::getId,
                        entity -> new PlacedSource(entity.getName(), isOwnedBy(entity, requester))));
    }

    // AgentVersionPolicy 가 AGENT_FOREIGN_NODE_INCLUDED 로 막는 것과 같은 기준이어야 한다.
    private static boolean isOwnedBy(NodeDefinitionEntity entity, UserId requester) {
        return UserId.of(entity.getOwnerId()).equals(requester);
    }
}
