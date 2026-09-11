package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AgentNodeSourceResolver implements NodeSourceResolver {
    @Override
    public SourceType sourceType() {
        return SourceType.AGENT;
    }

    @Override
    public Optional<GraphNode> resolve(NodePlacement placement) {
        return Optional.empty();
    }
}
