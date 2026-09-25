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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

            if (!node.isOwnedBy(requester)) {
                throw new CoreException(ErrorType.AGENT_FOREIGN_NODE_INCLUDED,
                        Map.of("nodeId", nodeId.value()));
            }
        }

        Set<AgentId> placedAgents = AgentPlacement.in(workflow.getGraph()).stream()
                .map(AgentPlacement::agentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<AgentId, Agent> agents = agentReader.readAll(placedAgents).stream()
                .collect(Collectors.toMap(Agent::getId, Function.identity()));

        for (AgentId agentId : placedAgents) {
            Agent agent = agents.get(agentId);

            if (agent == null || !agent.isOwnedBy(requester)) {
                throw new CoreException(ErrorType.AGENT_FOREIGN_NODE_INCLUDED,
                        Map.of("agentId", agentId.value()));
            }
        }

        return loaded;
    }
}
