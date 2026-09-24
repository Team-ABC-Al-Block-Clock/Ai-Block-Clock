package com.aiblockclock.domain.agent;

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
public class PublishedAgentSourceDescriber implements NodeSourceDescriber {
    private final AgentReader agentReader;

    @Override
    public SourceType sourceType() {
        return SourceType.AGENT;
    }

    // agent 행을 그대로 읽는다. readPublic(ids) 는 공개 버전을 타고 들어가서,
    // 놓은 뒤 공개를 내린 에이전트가 통째로 빠진다. 그러면 이름이 다시 비어 보인다.
    @Override
    public Map<UUID, PlacedSource> describe(UserId requester, List<NodeReference> references) {
        List<AgentId> ids = references.stream()
                .map(NodeReference::referenceId).distinct().map(AgentId::of).toList();

        return agentReader.readAll(ids).stream()
                .collect(Collectors.toUnmodifiableMap(
                        agent -> agent.getId().value(),
                        agent -> new PlacedSource(agent.getProfile().name(), agent.isOwnedBy(requester))));
    }
}
