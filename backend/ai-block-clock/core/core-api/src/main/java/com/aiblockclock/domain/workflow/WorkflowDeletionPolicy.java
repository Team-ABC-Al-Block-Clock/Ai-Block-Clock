package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class WorkflowDeletionPolicy {
    private final WorkflowUsageView usageView;

    public void ensureDeletable(Workflow workflow, OwnerId requester) {
        workflow.ensureOwner(requester);

        if (usageView.isSourceOfAnyVersion(workflow.getId())) {
            throw new CoreException(ErrorType.WORKFLOW_SOURCE_OF_AGENT_VERSION);
        }

        if (usageView.isExecuting(workflow.getId())) {
            throw new CoreException(ErrorType.WORKFLOW_EXECUTION_IN_PROGRESS);
        }
    }
}
