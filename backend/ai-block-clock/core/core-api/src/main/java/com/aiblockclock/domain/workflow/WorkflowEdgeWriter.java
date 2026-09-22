package com.aiblockclock.domain.workflow;

import com.aiblockclock.storage.workflow.WorkflowEdgeEntity;
import com.aiblockclock.storage.workflow.WorkflowEdgeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkflowEdgeWriter {
    private final WorkflowEdgeRepository workflowEdgeRepository;

    @Transactional
    public void append(WorkflowId workflowId, List<GraphEdge> edges) {
        workflowEdgeRepository.saveAll(WorkflowEntityConverter.toEdgeEntities(workflowId.value(), edges));
    }

    @Transactional
    public void replace(WorkflowId workflowId, List<GraphEdge> edges) {
        List<WorkflowEdgeEntity> rows = WorkflowEntityConverter.toEdgeEntities(workflowId.value(), edges);
        workflowEdgeRepository.deleteMissing(
                workflowId.value(), rows.stream().map(WorkflowEdgeEntity::getEdgeKey).toList());

        for (WorkflowEdgeEntity row : rows) {
            int updated = workflowEdgeRepository.updateEnds(
                    workflowId.value(), row.getEdgeKey(), row.getSourceNodeKey(), row.getTargetNodeKey());

            if (updated == 0) {
                workflowEdgeRepository.save(row);
            }
        }
    }

    @Transactional
    public void removeAll(WorkflowId workflowId) {
        workflowEdgeRepository.deleteAllByWorkflowId(workflowId.value());
    }
}
