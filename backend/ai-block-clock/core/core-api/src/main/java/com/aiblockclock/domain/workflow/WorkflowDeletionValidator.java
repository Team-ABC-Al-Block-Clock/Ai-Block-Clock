package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.storage.workflow.WorkflowEntity;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowDeletionValidator {
    private final WorkflowUsageView usageView;
    private final WorkflowWriteValidator writeValidator;

    public void ensureDeletable(WorkflowEntity entity, OwnerId requester) {
        writeValidator.ensureOwner(OwnerId.of(entity.getOwnerId()), requester);

        WorkflowId id = WorkflowId.of(entity.getId());
        if (usageView.isSourceOfAnyVersion(id)) {
            throw new CoreException(ErrorType.WORKFLOW_SOURCE_OF_AGENT_VERSION);
        }

        if (usageView.isExecuting(id)) {
            throw new CoreException(ErrorType.WORKFLOW_EXECUTION_IN_PROGRESS);
        }
    }
}
