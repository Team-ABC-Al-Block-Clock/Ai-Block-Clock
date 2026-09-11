package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.enums.workflow.WorkflowSort;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.support.paging.CursorPage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final WorkflowReader workflowReader;
    private final WorkflowWriter workflowWriter;
    private final NodeSourceRegistry nodeSourceRegistry;
    private final WorkflowDeletionPolicy deletionPolicy;

    public Workflow save(
            OwnerId requester, String name, List<NodePlacement> nodes, List<GraphEdge> edges) {
        WorkflowGraph graph = assemble(nodes, edges);
        Workflow workflow = Workflow.create(WorkflowId.newId(), requester, name, graph);
        return workflowWriter.append(workflow);
    }

    public Workflow get(WorkflowId id, OwnerId requester) {
        Workflow workflow = workflowReader.read(id);
        workflow.ensureOwner(requester);
        return workflow;
    }

    public Workflow replace(
            WorkflowId id,
            OwnerId requester,
            String name,
            Revision expectedRevision,
            List<NodePlacement> nodes,
            List<GraphEdge> edges
    ) {
        Workflow workflow = workflowReader.read(id);
        WorkflowGraph graph = assemble(nodes, edges);

        workflow.rename(requester, name);
        workflow.replaceGraph(requester, graph, expectedRevision);
        return workflowWriter.update(workflow);
    }

    public void delete(WorkflowId id, OwnerId requester) {
        Workflow workflow = workflowReader.read(id);
        deletionPolicy.ensureDeletable(workflow, requester);
        workflowWriter.remove(id);
    }

    public CursorPage<WorkflowSummary> findMine(
            OwnerId requester, WorkflowSort sort, String keyword, String cursor, int size) {
        return workflowReader.readMine(requester, sort, keyword, cursor, size);
    }

    private WorkflowGraph assemble(List<NodePlacement> nodes, List<GraphEdge> edges) {
        WorkflowGraph graph = WorkflowGraph.of(nodes.stream().map(this::resolve).toList(), edges);
        GraphLimits.defaults().ensureWithin(graph);
        return graph;
    }

    private GraphNode resolve(NodePlacement placement) {
        return nodeSourceRegistry.resolve(placement)
                .orElseThrow(() -> new CoreException(ErrorType.WORKFLOW_NODE_SOURCE_NOT_FOUND,
                        Map.of("nodeKey", placement.key().value(), "sourceId", placement.sourceId())));
    }
}
