package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.workflow.GraphConverter;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;
import com.aiblockclock.storage.graph.GraphEdgeRepository;
import com.aiblockclock.storage.graph.GraphNodeRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentVersionReader {
    private final AgentVersionRepository agentVersionRepository;
    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;

    public WorkflowGraph readGraph(AgentVersionId id) {
        return graphOf(entity(id).getGraphId());
    }

    public AgentVersion read(AgentVersionId id) {
        AgentVersionEntity entity = entity(id);

        return AgentVersion.restore(
                AgentVersionId.of(entity.getId()),
                AgentId.of(entity.getAgent().getId()),
                VersionNumber.of(entity.getVersionNo()),
                new SourceWorkflow(WorkflowId.of(entity.getSourceWorkflowId()),
                        Revision.of(entity.getSourceRevision())),
                new VersionCost(entity.getPromptNodeCount(), AbcAmount.of(entity.getBaseCost())),
                entity.getTier(),
                entity.getPublicationState());
    }

    private WorkflowGraph graphOf(UUID graphId) {
        return GraphConverter.toGraph(
                graphNodeRepository.findByGraphIdOrderByIdAsc(graphId),
                graphEdgeRepository.findByGraphIdOrderByIdAsc(graphId));
    }

    private AgentVersionEntity entity(AgentVersionId id) {
        return agentVersionRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.WORKFLOW_NODE_SOURCE_NOT_FOUND,
                        Map.of("versionId", id.value())));
    }
}
