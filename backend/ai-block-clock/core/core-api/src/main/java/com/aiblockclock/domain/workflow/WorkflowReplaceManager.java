package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;

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
            OwnerId requester,
            String name,
            Revision expectedRevision,
            List<NodePlacement> nodes,
            List<GraphEdge> edges
    ) {
        Workflow workflow = workflowReader.readForUpdate(id);
        workflow.ensureOwner(requester);

        WorkflowGraph graph = workflowGraphAssembler.assemble(nodes, edges);

        return workflowWriter.update(id, name, graph, workflow.nextRevisionFor(expectedRevision));
    }
}
