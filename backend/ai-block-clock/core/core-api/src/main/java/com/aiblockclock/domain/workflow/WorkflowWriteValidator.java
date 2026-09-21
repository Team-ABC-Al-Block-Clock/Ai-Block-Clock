package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.enums.workflow.ValidationStatus;
import com.aiblockclock.storage.workflow.WorkflowEntity;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WorkflowWriteValidator {
    public void ensureOwner(OwnerId owner, OwnerId requester) {
        if (!owner.equals(requester)) {
            throw new CoreException(ErrorType.WORKFLOW_FORBIDDEN);
        }
    }

    public GraphRevision ensureReplaceable(WorkflowEntity entity, OwnerId requester, Revision expected) {
        ensureOwner(OwnerId.of(entity.getOwnerId()), requester);

        Revision current = Revision.of(entity.getRevision());
        if (!current.equals(expected)) {
            throw new CoreException(ErrorType.WORKFLOW_REVISION_CONFLICT,
                    Map.of("expectedRevision", expected.value(), "currentRevision", current.value()));
        }

        return new GraphRevision(current.next(), ValidationStatus.NOT_VALIDATED);
    }
}
