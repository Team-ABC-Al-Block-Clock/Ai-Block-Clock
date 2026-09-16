package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;

import java.util.Optional;

public interface NodeSourceResolver {
    SourceType sourceType();

    Optional<GraphNode> resolve(NodePlacement placement);
}
