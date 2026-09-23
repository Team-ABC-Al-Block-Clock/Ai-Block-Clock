package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.node.RefFileReader;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphId;
import com.aiblockclock.domain.workflow.GraphWriter;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.domain.workflow.WorkflowValidator;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final NodeDefinitionReader nodeReader;
    private final RefFileReader refFileReader;
    private final AgentReader agentReader;
    private final AgentVersionReader agentVersionReader;
    private final AgentVersionPolicy versionPolicy;
    private final GraphWriter graphWriter;
    private final AgentWriter agentWriter;
    private final AgentVersionWriter agentVersionWriter;
    private final AgentVersionNodeWriter agentVersionNodeWriter;

    @Transactional
    public AgentRegistration register(
            UserId requester, Workflow source, Revision expectedRevision, AgentProfile profile) {
        WorkflowValidator.ensureRevision(source.getRevision(), expectedRevision);

        WorkflowGraph snapshot = source.getGraph();
        Map<NodeDefinitionId, NodeDefinition> placedNodes = lockAndValidate(source, requester, snapshot);

        GraphId graphId = copyGraph(snapshot);
        AgentRegistration registered = publishFirstVersion(
                requester, source, profile, VersionCostAssembler.assemble(snapshot, placedNodes));

        agentWriter.append(registered.agent());
        agentVersionWriter.append(registered.version(), graphId);
        agentVersionNodeWriter.append(registered.version().getId(),
                VersionNode.freeze(snapshot, placedNodes, refFilesOf(placedNodes)));

        return registered;
    }

    @Transactional
    public AgentRegistration update(
            UserId requester, AgentId agentId, Workflow source, Revision expectedRevision, AgentProfile profile) {
        WorkflowValidator.ensureRevision(source.getRevision(), expectedRevision);

        Agent agent = agentReader.read(agentId);
        agent.ensureOwner(requester);

        AgentVersion published = publishedVersionOf(agent);
        Agent described = agent.describedAs(profile);
        SourceWorkflow frozen = new SourceWorkflow(source.getId(), source.getRevision());

        if (frozen.equals(published.getSource())) {
            agentWriter.append(described);

            return new AgentRegistration(described, published);
        }

        WorkflowGraph snapshot = source.getGraph();
        Map<NodeDefinitionId, NodeDefinition> placedNodes = lockAndValidate(source, requester, snapshot);

        GraphId graphId = copyGraph(snapshot);
        AgentVersion next = AgentVersion.next(published, AgentVersionId.newId(),
                frozen, snapshot, VersionCostAssembler.assemble(snapshot, placedNodes)).publish();
        Agent republished = described.publish(requester, next.getId(), next.getNumber());

        agentWriter.append(republished);
        agentVersionWriter.append(next, graphId);
        agentVersionNodeWriter.append(next.getId(),
                VersionNode.freeze(snapshot, placedNodes, refFilesOf(placedNodes)));

        return new AgentRegistration(republished, next);
    }

    private Map<NodeDefinitionId, List<RefFile>> refFilesOf(
            Map<NodeDefinitionId, NodeDefinition> placedNodes) {
        return refFileReader.readAll(List.copyOf(placedNodes.keySet()));
    }

    private Map<NodeDefinitionId, NodeDefinition> lockAndValidate(
            Workflow source, UserId requester, WorkflowGraph snapshot) {
        List<NodeDefinition> locked = nodeReader.readAllForShare(placedNodeIds(snapshot));

        return versionPolicy.ensureVersionable(source, requester, locked);
    }

    private GraphId copyGraph(WorkflowGraph snapshot) {
        GraphId graphId = GraphId.newId();
        graphWriter.append(graphId, snapshot);

        return graphId;
    }

    private AgentVersion publishedVersionOf(Agent agent) {
        PublicVersion publicVersion = agent.getPublicVersion();
        if (publicVersion == null) {
            throw new CoreException(ErrorType.AGENT_NOT_FOUND);
        }

        return agentVersionReader.read(publicVersion.id());
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
