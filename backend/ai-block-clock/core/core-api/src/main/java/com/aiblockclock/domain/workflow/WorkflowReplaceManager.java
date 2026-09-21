package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkflowReplaceManager {
    private final WorkflowReader workflowReader;
    private final WorkflowWriter workflowWriter;
    private final WorkflowGraphAssembler workflowGraphAssembler;

    @Transactional
    public Workflow replace(
            WorkflowId id,
            UserId requester,
            String name,
            Revision expectedRevision,
            List<NodePlacement> nodes,
            List<GraphEdge> edges
    ) {
        Workflow workflow = workflowReader.readForUpdate(id);
        workflow.ensureOwner(requester);

        WorkflowGraph graph = workflowGraphAssembler.assemble(nodes, edges);

        workflow.rename(requester, name);
        workflow.replaceGraph(requester, graph, expectedRevision);
        return workflowWriter.update(workflow);
    }
}
