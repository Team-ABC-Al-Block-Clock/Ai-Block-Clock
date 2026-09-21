package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public final class AgentVersionPolicy {
    private final AgentOwnershipView agentOwnershipView;
    private final NodeDefinitionOwnershipView nodeOwnershipView;

    public void ensureVersionable(Workflow workflow, OwnerId requester) {
        workflow.ensureOwner(requester);

        if (!workflow.isValidated()) {
            throw new CoreException(ErrorType.AGENT_WORKFLOW_NOT_VALIDATED);
        }

        for (NodeReference reference : workflow.getGraph().baseNodeReferences()) {
            NodeDefinitionId nodeId = NodeDefinitionId.of(reference.referenceId());
            if (!nodeOwnershipView.isOwnedBy(nodeId, requester)) {
                throw new CoreException(ErrorType.AGENT_FOREIGN_NODE_INCLUDED,
                        Map.of("nodeId", nodeId.value()));
            }
        }

        for (AgentPlacement placement : AgentPlacement.in(workflow.getGraph())) {
            if (!agentOwnershipView.isOwnedBy(placement.agentId(), requester)) {
                throw new CoreException(ErrorType.AGENT_FOREIGN_NODE_INCLUDED,
                        Map.of("agentId", placement.agentId().value()));
            }
        }
    }
}
