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
    private final GraphWriter graphWriter;
    private final WorkflowGraphAssembler workflowGraphAssembler;
    private final WorkflowGraphDescriber workflowGraphDescriber;
    private final WorkflowValidator validator;

    @Transactional
    public Workflow save(
            UserId requester,
            String name,
            String description,
            List<NodePlacement> nodes,
            List<GraphEdge> edges
    ) {
        WorkflowGraph graph = workflowGraphAssembler.lockOrCreateSourcesAndAssemble(requester, nodes, edges);
        GraphId graphId = GraphId.newId();
        graphWriter.append(graphId, graph);

        return workflowWriter.append(
                Workflow.create(WorkflowId.newId(), requester, name, description, graph), graphId);
    }

    public Workflow get(WorkflowId id, UserId requester) {
        Workflow workflow = workflowReader.read(id);
        WorkflowValidator.ensureOwner(workflow.getOwner(), requester);

        return workflow;
    }

    /** 캔버스를 되살리는 조회. 박힌 참조를 이름과 소유 판정까지 풀어서 돌려준다. */
    @Transactional(readOnly = true)
    public WorkflowDetail getDetail(WorkflowId id, UserId requester) {
        Workflow workflow = get(id, requester);

        return new WorkflowDetail(workflow, workflowGraphDescriber.describe(requester, workflow.getGraph()));
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
        ReplaceTarget target = validator.ensureReplaceable(id, requester, expectedRevision);
        WorkflowGraph graph = workflowGraphAssembler.lockOrCreateSourcesAndAssemble(requester, nodes, edges);
        graphWriter.replace(target.graphId(), graph);

        return workflowWriter.revise(id, name, target.revised(), graph);
    }

    @Transactional
    public void delete(WorkflowId id, UserId requester) {
        GraphId graphId = validator.ensureDeletable(id, requester);

        workflowWriter.remove(id);
        graphWriter.removeAll(graphId);
    }

    public CursorPage<WorkflowSummary> findMine(
            UserId requester, WorkflowSort sort, String keyword, String cursor, int size) {
        return workflowReader.readMine(requester, sort, keyword, cursor, size);
    }
}
