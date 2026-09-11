package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;

import java.util.UUID;

public record BaseNode(NodeKey key, UUID sourceId) implements GraphNode {
    @Override
    public SourceType sourceType() {
        return SourceType.NODE;
    }

    @Override
    public NodeReference reference() {
        return NodeReference.of(sourceId);
    }
}
