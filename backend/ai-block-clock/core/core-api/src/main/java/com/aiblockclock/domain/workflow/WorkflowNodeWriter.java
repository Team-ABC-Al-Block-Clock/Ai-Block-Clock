package com.aiblockclock.domain.workflow;

import com.aiblockclock.storage.workflow.WorkflowNodeEntity;
import com.aiblockclock.storage.workflow.WorkflowNodeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkflowNodeWriter {
    private final WorkflowNodeRepository workflowNodeRepository;

    @Transactional
    public void append(WorkflowId workflowId, List<GraphNode> nodes) {
        workflowNodeRepository.saveAll(WorkflowEntityConverter.toNodeEntities(workflowId.value(), nodes));
    }

    @Transactional
    public void replace(WorkflowId workflowId, List<GraphNode> nodes) {
        List<WorkflowNodeEntity> rows = WorkflowEntityConverter.toNodeEntities(workflowId.value(), nodes);
        workflowNodeRepository.deleteMissing(
                workflowId.value(), rows.stream().map(WorkflowNodeEntity::getNodeKey).toList());

        for (WorkflowNodeEntity row : rows) {
            int updated = workflowNodeRepository.updatePlacement(workflowId.value(), row.getNodeKey(),
                    row.getSourceType(), row.getReferenceId(), row.getVersionId(), row.getInputSource());

            if (updated == 0) {
                workflowNodeRepository.save(row);
            }
        }
    }

    @Transactional
    public void removeAll(WorkflowId workflowId) {
        workflowNodeRepository.deleteAllByWorkflowId(workflowId.value());
    }
}
