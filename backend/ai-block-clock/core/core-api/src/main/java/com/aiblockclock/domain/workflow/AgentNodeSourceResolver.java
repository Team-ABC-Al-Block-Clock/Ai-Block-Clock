package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.SourceType;

import org.springframework.stereotype.Component;

import java.util.Optional;

// 에이전트 애그리거트 연동 전까지 모든 AGENT 배치를 WORKFLOW_015 로 거부한다.
@Component
public class AgentNodeSourceResolver implements NodeSourceResolver {
    @Override
    public SourceType sourceType() {
        return SourceType.AGENT;
    }

    @Override
    public Optional<GraphNode> resolve(UserId requester, NodePlacement placement) {
        return Optional.empty();
    }
}
