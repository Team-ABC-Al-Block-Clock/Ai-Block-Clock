package com.aiblockclock.domain.workflow;

public interface WorkflowUsageView {
    boolean isExecuting(WorkflowId workflowId);
}
