package com.aiblockclock.domain.node;

import com.aiblockclock.storage.workflow.WorkflowNodeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NodeDefinitionUsageReader {
    private final WorkflowNodeRepository workflowNodeRepository;

    public boolean isPlacedInAnyWorkflow(NodeDefinitionId nodeId) {
        return workflowNodeRepository.existsByReferenceId(nodeId.value());
    }
}
