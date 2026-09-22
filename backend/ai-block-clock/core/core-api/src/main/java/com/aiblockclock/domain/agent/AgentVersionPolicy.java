package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowValidator;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AgentVersionPolicy {
    private final AgentReader agentReader;

    public Map<NodeDefinitionId, NodeDefinition> ensureVersionable(
            Workflow workflow, UserId requester, List<NodeDefinition> placedNodes) {
        WorkflowValidator.ensureOwner(workflow.getOwner(), requester);

        Map<NodeDefinitionId, NodeDefinition> loaded = placedNodes.stream()
                .collect(Collectors.toMap(NodeDefinition::getId, Function.identity()));

        for (NodeReference reference : workflow.getGraph().baseNodeReferences()) {
            NodeDefinitionId nodeId = NodeDefinitionId.of(reference.referenceId());
            NodeDefinition node = loaded.get(nodeId);

            if (node == null) {
                throw new CoreException(ErrorType.WORKFLOW_NODE_SOURCE_NOT_FOUND,
                        Map.of("nodeId", nodeId.value()));
            }

            if (!node.getOwner().equals(requester)) {
                throw new CoreException(ErrorType.AGENT_FOREIGN_NODE_INCLUDED,
                        Map.of("nodeId", nodeId.value()));
            }
        }

        for (AgentPlacement placement : AgentPlacement.in(workflow.getGraph())) {
            if (!agentReader.isOwnedBy(placement.agentId(), requester)) {
                throw new CoreException(ErrorType.AGENT_FOREIGN_NODE_INCLUDED,
                        Map.of("agentId", placement.agentId().value()));
            }
        }

        return loaded;
    }
}
