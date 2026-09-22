package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.enums.workflow.ValidationStatus;
import com.aiblockclock.storage.workflow.WorkflowEntity;
import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkflowValidator {
    private final WorkflowRepository workflowRepository;
    private final WorkflowUsageView usageView;

    public static void ensureOwner(OwnerId owner, OwnerId requester) {
        if (!owner.equals(requester)) {
            throw new CoreException(ErrorType.WORKFLOW_FORBIDDEN);
        }
    }

    @Transactional
    public GraphRevision ensureReplaceable(WorkflowId id, OwnerId requester, Revision expected) {
        WorkflowEntity entity = lock(id);
        ensureOwner(OwnerId.of(entity.getOwnerId()), requester);

        Revision current = Revision.of(entity.getRevision());
        if (!current.equals(expected)) {
            throw new CoreException(ErrorType.WORKFLOW_REVISION_CONFLICT,
                    Map.of("expectedRevision", expected.value(), "currentRevision", current.value()));
        }

        return new GraphRevision(current.next(), ValidationStatus.NOT_VALIDATED);
    }

    @Transactional
    public void ensureDeletable(WorkflowId id, OwnerId requester) {
        WorkflowEntity entity = lock(id);
        ensureOwner(OwnerId.of(entity.getOwnerId()), requester);

        if (usageView.isSourceOfAnyVersion(id)) {
            throw new CoreException(ErrorType.WORKFLOW_SOURCE_OF_AGENT_VERSION);
        }

        if (usageView.isExecuting(id)) {
            throw new CoreException(ErrorType.WORKFLOW_EXECUTION_IN_PROGRESS);
        }
    }

    private WorkflowEntity lock(WorkflowId id) {
        return workflowRepository.findByIdForUpdate(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.WORKFLOW_NOT_FOUND));
    }
}
