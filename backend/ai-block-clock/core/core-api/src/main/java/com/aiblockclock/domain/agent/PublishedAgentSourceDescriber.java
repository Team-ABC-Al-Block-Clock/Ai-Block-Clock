package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.NodeSourceDescriber;
import com.aiblockclock.domain.workflow.PlacedSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.agent.AgentEntity;
import com.aiblockclock.storage.agent.AgentRepository;

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
public class PublishedAgentSourceDescriber implements NodeSourceDescriber {
    private final AgentRepository agentRepository;

    @Override
    public SourceType sourceType() {
        return SourceType.AGENT;
    }

    // agent 행을 그대로 읽는다. readPublic(ids) 는 공개 버전을 타고 들어가서,
    // 놓은 뒤 공개를 내린 에이전트가 통째로 빠진다. 그러면 이름이 다시 비어 보인다.
    @Override
    public Map<UUID, PlacedSource> describe(UserId requester, List<NodeReference> references) {
        List<UUID> ids = references.stream().map(NodeReference::referenceId).distinct().toList();

        if (ids.isEmpty()) {
            return Map.of();
        }

        return agentRepository.findAllById(ids).stream()
                .collect(Collectors.toUnmodifiableMap(
                        AgentEntity::getId,
                        entity -> new PlacedSource(entity.getName(), isOwnedBy(entity, requester))));
    }

    // AgentVersionPolicy 가 AGENT_FOREIGN_NODE_INCLUDED 로 막는 것과 같은 기준이어야 한다.
    private static boolean isOwnedBy(AgentEntity entity, UserId requester) {
        return UserId.of(entity.getOwnerId()).equals(requester);
    }
}
