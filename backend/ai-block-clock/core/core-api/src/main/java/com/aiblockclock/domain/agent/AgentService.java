package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphId;
import com.aiblockclock.domain.workflow.GraphWriter;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.domain.workflow.WorkflowValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final NodeDefinitionReader nodeReader;
    private final AgentVersionPolicy versionPolicy;
    private final GraphWriter graphWriter;
    private final AgentWriter agentWriter;
    private final AgentVersionWriter agentVersionWriter;

    @Transactional
    public AgentRegistration register(
            UserId requester, Workflow source, Revision expectedRevision, AgentProfile profile) {
        WorkflowValidator.ensureRevision(source.getRevision(), expectedRevision);

        WorkflowGraph snapshot = source.getGraph();
        List<NodeDefinition> placedNodes = nodeReader.readAll(placedNodeIds(snapshot));
        versionPolicy.ensureVersionable(source, requester, placedNodes);

        GraphId graphId = copyGraph(snapshot);
        AgentRegistration registered = publishFirstVersion(
                requester, source, profile, VersionCostAssembler.assemble(snapshot, placedNodes));

        agentWriter.append(registered.agent());
        agentVersionWriter.append(registered.version(), graphId);

        return registered;
    }

    private GraphId copyGraph(WorkflowGraph snapshot) {
        GraphId graphId = GraphId.newId();
        graphWriter.append(graphId, snapshot);

        return graphId;
    }

    private static AgentRegistration publishFirstVersion(
            UserId requester, Workflow source, AgentProfile profile, VersionCost cost) {
        AgentId agentId = AgentId.newId();
        AgentVersion version = AgentVersion.first(
                AgentVersionId.newId(),
                agentId,
                new SourceWorkflow(source.getId(), source.getRevision()),
                source.getGraph(),
                cost).publish();
        Agent agent = Agent.draft(agentId, requester, profile)
                .publish(requester, version.getId(), version.getNumber());

        return new AgentRegistration(agent, version);
    }

    private static List<NodeDefinitionId> placedNodeIds(WorkflowGraph snapshot) {
        return snapshot.baseNodeReferences().stream()
                .map(NodeReference::referenceId)
                .map(NodeDefinitionId::of)
                .distinct()
                .toList();
    }
}
