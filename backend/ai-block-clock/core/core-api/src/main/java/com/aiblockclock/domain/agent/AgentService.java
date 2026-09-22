package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphEdgeWriter;
import com.aiblockclock.domain.workflow.GraphId;
import com.aiblockclock.domain.workflow.GraphNodeWriter;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.domain.workflow.WorkflowReader;
import com.aiblockclock.domain.workflow.WorkflowValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final WorkflowReader workflowReader;
    private final AgentVersionPolicy versionPolicy;
    private final VersionCostAssembler costAssembler;
    private final GraphNodeWriter graphNodeWriter;
    private final GraphEdgeWriter graphEdgeWriter;
    private final AgentWriter agentWriter;
    private final AgentVersionWriter agentVersionWriter;

    @Transactional
    public AgentRegistration register(
            UserId requester, WorkflowId workflowId, Revision expectedRevision, AgentProfile profile) {
        Workflow workflow = workflowReader.read(workflowId);
        WorkflowValidator.ensureRevision(workflow.getRevision(), expectedRevision);
        versionPolicy.ensureVersionable(workflow, requester);

        WorkflowGraph snapshot = workflow.getGraph();
        GraphId graphId = GraphId.newId();
        graphNodeWriter.append(graphId, snapshot.getNodes());
        graphEdgeWriter.append(graphId, snapshot.getEdges());

        AgentId agentId = AgentId.newId();
        AgentVersion version = AgentVersion.first(
                AgentVersionId.newId(),
                agentId,
                new SourceWorkflow(workflowId, workflow.getRevision()),
                snapshot,
                costAssembler.assemble(snapshot)).publish();
        Agent agent = Agent.draft(agentId, requester, profile)
                .publish(requester, version.getId(), version.getNumber());

        agentWriter.append(agent);
        agentVersionWriter.append(version, graphId);

        return new AgentRegistration(agent, version);
    }
}
