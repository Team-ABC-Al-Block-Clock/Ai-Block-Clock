package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.util.Map;
import java.util.UUID;

public record AgentNode(NodeKey key, UUID agentId, UUID versionId) implements GraphNode {
    public AgentNode {
        if (versionId == null) {
            throw new CoreException(ErrorType.WORKFLOW_NODE_REFERENCE_MISMATCH,
                    Map.of("nodeKey", key.value(), "sourceType", SourceType.AGENT.name()));
        }
    }

    @Override
    public SourceType sourceType() {
        return SourceType.AGENT;
    }

    @Override
    public NodeReference reference() {
        return NodeReference.of(agentId, versionId);
    }
}
