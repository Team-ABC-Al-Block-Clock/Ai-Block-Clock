package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.Timestamps;
import com.aiblockclock.enums.workflow.ValidationStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Workflow {
    private final WorkflowId id;
    private final OwnerId owner;
    private final String name;
    private final String description;
    private final WorkflowGraph graph;
    private final ValidationStatus validationStatus;
    private final Revision revision;
    private final Timestamps timestamps;
    private final Instant lastExecutedAt;

    public static Workflow create(
            WorkflowId id, OwnerId owner, String name, String description, WorkflowGraph graph) {
        return new Workflow(id, owner, name, description, graph,
                ValidationStatus.NOT_VALIDATED, Revision.initial(), Timestamps.unsaved(), null);
    }

    public static Workflow restore(
            WorkflowId id,
            OwnerId owner,
            String name,
            String description,
            WorkflowGraph graph,
            ValidationStatus validationStatus,
            Revision revision,
            Timestamps timestamps,
            Instant lastExecutedAt
    ) {
        return new Workflow(id, owner, name, description, graph, validationStatus, revision,
                timestamps, lastExecutedAt);
    }

    public GraphRevision nextRevisionFor(Revision expected) {
        if (!revision.equals(expected)) {
            throw new CoreException(ErrorType.WORKFLOW_REVISION_CONFLICT,
                    Map.of("expectedRevision", expected.value(), "currentRevision", revision.value()));
        }

        return new GraphRevision(revision.next(), ValidationStatus.NOT_VALIDATED);
    }

    public boolean isValidated() {
        return validationStatus == ValidationStatus.VALID;
    }

    public boolean isOwnedBy(OwnerId candidate) {
        return owner.equals(candidate);
    }

    public void ensureOwner(OwnerId requester) {
        if (!isOwnedBy(requester)) {
            throw new CoreException(ErrorType.WORKFLOW_FORBIDDEN);
        }
    }
}
