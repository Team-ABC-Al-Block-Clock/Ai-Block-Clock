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
    public Workflow append(Workflow workflow, GraphId graphId) {
        WorkflowEntity saved = workflowRepository.save(WorkflowEntityConverter.toEntity(workflow, graphId));

        return WorkflowEntityConverter.toDomain(saved, workflow.getGraph());
    }

    @Transactional
    public Workflow revise(WorkflowId id, String name, GraphRevision revised, WorkflowGraph graph) {
        WorkflowEntity entity = workflowRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.WORKFLOW_NOT_FOUND));

        entity.rename(name);
        entity.revise(revised.validationStatus(), revised.revision().value());

        return WorkflowEntityConverter.toDomain(entity, graph);
    }

    @Transactional
    public void remove(WorkflowId id) {
        workflowRepository.deleteById(id.value());
    }
}
