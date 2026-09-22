package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.node.NodeType;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import java.util.UUID;

public record NodePlacement(
        NodeKey key,
        SourceType sourceType,
        NodeType nodeType,
        UUID sourceId,
        InputSource inputSource,
        NodeDraft draft) {
    public NodePlacement(NodeKey key, SourceType sourceType, UUID sourceId, InputSource inputSource) {
        this(key, sourceType, null, sourceId, inputSource, null);
    }
}
