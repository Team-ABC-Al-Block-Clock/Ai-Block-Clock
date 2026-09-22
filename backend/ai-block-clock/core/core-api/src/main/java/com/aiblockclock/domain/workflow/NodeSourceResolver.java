package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.SourceType;

import java.util.Optional;

public interface NodeSourceResolver {
    SourceType sourceType();

    Optional<GraphNode> resolve(UserId requester, NodePlacement placement);
}
