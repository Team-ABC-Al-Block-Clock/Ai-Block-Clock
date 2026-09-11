package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BaseNodeSourceResolver implements NodeSourceResolver {
    @Override
    public SourceType sourceType() {
        return SourceType.NODE;
    }

    @Override
    public Optional<GraphNode> resolve(NodePlacement placement) {
        return Optional.of(new BaseNode(placement.key(), placement.sourceId()));
    }
}
