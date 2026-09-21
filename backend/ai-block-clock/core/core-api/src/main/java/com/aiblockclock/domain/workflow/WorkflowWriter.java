package com.aiblockclock.domain.workflow;

import com.aiblockclock.storage.workflow.WorkflowEdgeEntity;
import com.aiblockclock.storage.workflow.WorkflowEdgeRepository;
import com.aiblockclock.storage.workflow.WorkflowEntity;
import com.aiblockclock.storage.workflow.WorkflowNodeEntity;
import com.aiblockclock.storage.workflow.WorkflowNodeRepository;
import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WorkflowWriter {
    private final WorkflowRepository workflowRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final WorkflowEdgeRepository workflowEdgeRepository;

    @Transactional
    public Workflow append(Workflow workflow) {
        WorkflowEntity saved = workflowRepository.save(WorkflowEntityConverter.toEntity(workflow));

        List<WorkflowNodeEntity> nodes = workflowNodeRepository.saveAll(
                WorkflowEntityConverter.toNodeEntities(saved.getId(), workflow.getGraph()));
        List<WorkflowEdgeEntity> edges = workflowEdgeRepository.saveAll(
                WorkflowEntityConverter.toEdgeEntities(saved.getId(), workflow.getGraph()));

        return WorkflowEntityConverter.toDomain(saved, nodes, edges);
    }

    @Transactional
    public Workflow update(Workflow workflow) {
        WorkflowEntity entity = workflowRepository.findById(workflow.getId().value())
                .orElseThrow(() -> new CoreException(ErrorType.WORKFLOW_NOT_FOUND));

        entity.rename(workflow.getName());
        entity.revise(workflow.getValidationStatus(), workflow.getRevision().value());

        List<WorkflowNodeEntity> nodes = mergeNodes(entity.getId(), workflow.getGraph());
        List<WorkflowEdgeEntity> edges = mergeEdges(entity.getId(), workflow.getGraph());

        return WorkflowEntityConverter.toDomain(entity, nodes, edges);
    }

    @Transactional
    public void remove(WorkflowId id) {
        workflowEdgeRepository.deleteAllByWorkflowId(id.value());
        workflowNodeRepository.deleteAllByWorkflowId(id.value());
        workflowRepository.deleteById(id.value());
    }

    private List<WorkflowNodeEntity> mergeNodes(UUID workflowId, WorkflowGraph graph) {
        Map<String, WorkflowNodeEntity> gone = new HashMap<>();
        workflowNodeRepository.findByWorkflowIdOrderByIdAsc(workflowId)
                .forEach(node -> gone.put(node.getNodeKey(), node));

        List<WorkflowNodeEntity> merged = new ArrayList<>();
        List<WorkflowNodeEntity> added = new ArrayList<>();
        for (WorkflowNodeEntity incoming : WorkflowEntityConverter.toNodeEntities(workflowId, graph)) {
            WorkflowNodeEntity kept = gone.remove(incoming.getNodeKey());
            if (kept == null) {
                added.add(incoming);
                continue;
            }

            kept.refer(incoming.getSourceType(), incoming.getReferenceId(), incoming.getVersionId(),
                    incoming.getInputSource());
            merged.add(kept);
        }

        workflowNodeRepository.deleteAll(gone.values());
        merged.addAll(workflowNodeRepository.saveAll(added));
        return merged;
    }

    private List<WorkflowEdgeEntity> mergeEdges(UUID workflowId, WorkflowGraph graph) {
        Map<String, WorkflowEdgeEntity> gone = new HashMap<>();
        workflowEdgeRepository.findByWorkflowIdOrderByIdAsc(workflowId)
                .forEach(edge -> gone.put(edge.getEdgeKey(), edge));

        List<WorkflowEdgeEntity> merged = new ArrayList<>();
        List<WorkflowEdgeEntity> added = new ArrayList<>();
        for (WorkflowEdgeEntity incoming : WorkflowEntityConverter.toEdgeEntities(workflowId, graph)) {
            WorkflowEdgeEntity kept = gone.remove(incoming.getEdgeKey());
            if (kept == null) {
                added.add(incoming);
                continue;
            }

            kept.connect(incoming.getSourceNodeKey(), incoming.getTargetNodeKey());
            merged.add(kept);
        }

        workflowEdgeRepository.deleteAll(gone.values());
        merged.addAll(workflowEdgeRepository.saveAll(added));
        return merged;
    }
}
