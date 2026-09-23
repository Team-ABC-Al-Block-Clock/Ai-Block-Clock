package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.AgentNode;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.NodeSourceResolver;
import com.aiblockclock.enums.workflow.SourceType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PublishedAgentSourceResolver implements NodeSourceResolver {
    private final AgentReader agentReader;

    @Override
    public SourceType sourceType() {
        return SourceType.AGENT;
    }

    @Override
    public Optional<GraphNode> resolve(UserId requester, NodePlacement placement) {
        return agentReader.findPublicVersion(AgentId.of(placement.sourceId()))
                .map(versionId -> new AgentNode(
                        placement.key(), placement.sourceId(), versionId.value(), placement.inputSource()));
    }
}
