package com.aiblockclock.domain.workflow;

public interface WorkflowUsageView {
    boolean isSourceOfAnyVersion(WorkflowId workflowId);

    boolean isExecuting(WorkflowId workflowId);
}
