package com.aiblockclock.domain.workflow;

import com.aiblockclock.storage.workflow.WorkflowEntity;
import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WorkflowWriter {
    private final WorkflowRepository workflowRepository;

    @Transactional
    public Workflow append(Workflow workflow) {
        WorkflowEntity saved = workflowRepository.save(WorkflowEntityConverter.toEntity(workflow));
        return WorkflowEntityConverter.toDomain(saved);
    }

    @Transactional
    public Workflow update(Workflow workflow) {
        WorkflowEntity entity = workflowRepository.findById(workflow.getId().value())
                .orElseThrow(() -> new CoreException(ErrorType.WORKFLOW_NOT_FOUND));

        entity.rename(workflow.getName());

        entity.applyGraph(
                WorkflowEntityConverter.toNodeEntities(workflow.getGraph()),
                WorkflowEntityConverter.toEdgeEntities(workflow.getGraph()),
                workflow.getValidationStatus(),
                workflow.getRevision().value());

        return WorkflowEntityConverter.toDomain(workflowRepository.save(entity));
    }

    @Transactional
    public void remove(WorkflowId id) {
        workflowRepository.deleteById(id.value());
    }
}
