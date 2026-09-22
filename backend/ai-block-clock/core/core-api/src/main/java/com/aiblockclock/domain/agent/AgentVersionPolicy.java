package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowValidator;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentVersionPolicy {
    private final AgentReader agentReader;

    public void ensureVersionable(Workflow workflow, UserId requester, List<NodeDefinition> placedNodes) {
        WorkflowValidator.ensureOwner(workflow.getOwner(), requester);

        for (NodeDefinition node : placedNodes) {
            if (!node.getOwner().equals(requester)) {
                throw new CoreException(ErrorType.AGENT_FOREIGN_NODE_INCLUDED,
                        Map.of("nodeId", node.getId().value()));
            }
        }

        for (AgentPlacement placement : AgentPlacement.in(workflow.getGraph())) {
            if (!agentReader.isOwnedBy(placement.agentId(), requester)) {
                throw new CoreException(ErrorType.AGENT_FOREIGN_NODE_INCLUDED,
                        Map.of("agentId", placement.agentId().value()));
            }
        }
    }
}
