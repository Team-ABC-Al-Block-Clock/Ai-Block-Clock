package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.WorkflowSort;
import com.aiblockclock.support.paging.CursorPage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final WorkflowReader workflowReader;
    private final WorkflowWriter workflowWriter;
    private final WorkflowReplaceManager workflowReplaceManager;
    private final WorkflowGraphAssembler workflowGraphAssembler;
    private final WorkflowDeletionPolicy deletionPolicy;

    public Workflow save(
            UserId requester,
            String name,
            String description,
            List<NodePlacement> nodes,
            List<GraphEdge> edges
    ) {
        WorkflowGraph graph = workflowGraphAssembler.assemble(nodes, edges);
        Workflow workflow = Workflow.create(WorkflowId.newId(), requester, name, description, graph);
        return workflowWriter.append(workflow);
    }

    public Workflow get(WorkflowId id, UserId requester) {
        Workflow workflow = workflowReader.read(id);
        workflow.ensureOwner(requester);
        return workflow;
    }

    public Workflow replace(
            WorkflowId id,
            UserId requester,
            String name,
            Revision expectedRevision,
            List<NodePlacement> nodes,
            List<GraphEdge> edges
    ) {
        return workflowReplaceManager.replace(id, requester, name, expectedRevision, nodes, edges);
    }

    public void delete(WorkflowId id, UserId requester) {
        Workflow workflow = workflowReader.read(id);
        deletionPolicy.ensureDeletable(workflow, requester);
        workflowWriter.remove(id);
    }

    public CursorPage<WorkflowSummary> findMine(
            UserId requester, WorkflowSort sort, String keyword, String cursor, int size) {
        return workflowReader.readMine(requester, sort, keyword, cursor, size);
    }
}
