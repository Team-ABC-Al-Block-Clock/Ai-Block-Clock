package com.aiblockclock.domain.node;

import com.aiblockclock.storage.workflow.WorkflowNodeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NodeDefinitionUsageReader implements NodeDefinitionUsageView {
    private final WorkflowNodeRepository workflowNodeRepository;

    @Override
    public boolean isPlacedInAnyWorkflow(NodeDefinitionId nodeId) {
        return workflowNodeRepository.existsByReferenceId(nodeId.value());
    }
}
