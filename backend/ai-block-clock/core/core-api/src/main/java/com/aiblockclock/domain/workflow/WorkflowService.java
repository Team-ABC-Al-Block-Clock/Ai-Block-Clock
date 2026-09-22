package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.WorkflowSort;
import com.aiblockclock.support.paging.CursorPage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final WorkflowReader workflowReader;
    private final WorkflowWriter workflowWriter;
    private final WorkflowNodeWriter workflowNodeWriter;
    private final WorkflowEdgeWriter workflowEdgeWriter;
    private final WorkflowGraphAssembler workflowGraphAssembler;
    private final WorkflowValidator validator;

    @Transactional
    public Workflow save(
            UserId requester,
            String name,
            String description,
            List<NodePlacement> nodes,
            List<GraphEdge> edges
    ) {
        WorkflowGraph graph = workflowGraphAssembler.assembleWithSourceLock(nodes, edges);
        Workflow saved = workflowWriter.append(
                Workflow.create(WorkflowId.newId(), requester, name, description, graph));
        workflowNodeWriter.append(saved.getId(), graph.getNodes());
        workflowEdgeWriter.append(saved.getId(), graph.getEdges());

        return saved;
    }

    public Workflow get(WorkflowId id, UserId requester) {
        Workflow workflow = workflowReader.read(id);
        WorkflowValidator.ensureOwner(workflow.getOwner(), requester);

        return workflow;
    }

    @Transactional
    public Workflow replace(
            WorkflowId id,
            UserId requester,
            String name,
            Revision expectedRevision,
            List<NodePlacement> nodes,
            List<GraphEdge> edges
    ) {
        GraphRevision revised = validator.ensureReplaceable(id, requester, expectedRevision);
        WorkflowGraph graph = workflowGraphAssembler.assembleWithSourceLock(nodes, edges);
        workflowNodeWriter.replace(id, graph.getNodes());
        workflowEdgeWriter.replace(id, graph.getEdges());

        return workflowWriter.revise(id, name, revised, graph);
    }

    @Transactional
    public void delete(WorkflowId id, UserId requester) {
        validator.ensureDeletable(id, requester);

        workflowEdgeWriter.removeAll(id);
        workflowNodeWriter.removeAll(id);
        workflowWriter.remove(id);
    }

    public CursorPage<WorkflowSummary> findMine(
            UserId requester, WorkflowSort sort, String keyword, String cursor, int size) {
        return workflowReader.readMine(requester, sort, keyword, cursor, size);
    }
}
