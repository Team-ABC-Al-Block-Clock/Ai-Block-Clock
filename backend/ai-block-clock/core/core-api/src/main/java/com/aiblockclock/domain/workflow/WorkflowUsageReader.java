package com.aiblockclock.domain.workflow;

import org.springframework.stereotype.Component;

@Component
public class WorkflowUsageReader implements WorkflowUsageView {
    @Override
    public boolean isSourceOfAnyVersion(WorkflowId workflowId) {
        return false;
    }

    @Override
    public boolean isExecuting(WorkflowId workflowId) {
        return false;
    }
}
