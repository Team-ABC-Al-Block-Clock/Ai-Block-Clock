package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphId;
import com.aiblockclock.domain.workflow.GraphWriter;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.domain.workflow.WorkflowValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentVersionPolicy versionPolicy;
    private final VersionCostAssembler costAssembler;
    private final GraphWriter graphWriter;
    private final AgentWriter agentWriter;
    private final AgentVersionWriter agentVersionWriter;

    @Transactional
    public AgentRegistration register(
            UserId requester, Workflow source, Revision expectedRevision, AgentProfile profile) {
        WorkflowValidator.ensureRevision(source.getRevision(), expectedRevision);
        versionPolicy.ensureVersionable(source, requester);

        WorkflowGraph snapshot = source.getGraph();
        GraphId graphId = GraphId.newId();
        graphWriter.append(graphId, snapshot);

        AgentId agentId = AgentId.newId();
        AgentVersion version = AgentVersion.first(
                AgentVersionId.newId(),
                agentId,
                new SourceWorkflow(source.getId(), source.getRevision()),
                snapshot,
                costAssembler.assemble(snapshot)).publish();
        Agent agent = Agent.draft(agentId, requester, profile)
                .publish(requester, version.getId(), version.getNumber());

        agentWriter.append(agent);
        agentVersionWriter.append(version, graphId);

        return new AgentRegistration(agent, version);
    }
}
