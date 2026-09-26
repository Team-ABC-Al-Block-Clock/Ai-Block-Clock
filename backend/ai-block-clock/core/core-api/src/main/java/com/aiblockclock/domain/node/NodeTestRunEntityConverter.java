package com.aiblockclock.domain.node;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.node.NodeTestRunEntity;

public final class NodeTestRunEntityConverter {
    private NodeTestRunEntityConverter() {
    }

    public static NodeTestRun toDomain(NodeTestRunEntity entity) {
        return new NodeTestRun(
                NodeTestRunId.of(entity.getId()),
                NodeDefinitionId.of(entity.getNodeId()),
                UserId.of(entity.getRequesterId()),
                entity.getRunId(),
                entity.isMock(),
                entity.getStatus(),
                entity.getCostUsd(),
                entity.getFailureCode(),
                entity.getRequestedAt(),
                entity.getFinishedAt());
    }
}
