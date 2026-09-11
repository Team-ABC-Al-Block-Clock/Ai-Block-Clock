package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.enums.workflow.ValidationStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Workflow {
    private final WorkflowId id;
    private final OwnerId owner;

    private String name;
    private WorkflowGraph graph;
    private ValidationStatus validationStatus;
    private Revision revision;

    private final WorkflowTimestamps timestamps;

    public static Workflow create(WorkflowId id, OwnerId owner, String name, WorkflowGraph graph) {
        return new Workflow(id, owner, name, graph,
                ValidationStatus.NOT_VALIDATED, Revision.initial(), WorkflowTimestamps.unsaved());
    }

    public static Workflow restore(
            WorkflowId id,
            OwnerId owner,
            String name,
            WorkflowGraph graph,
            ValidationStatus validationStatus,
            Revision revision,
            WorkflowTimestamps timestamps
    ) {
        return new Workflow(id, owner, name, graph, validationStatus, revision, timestamps);
    }

    public void rename(OwnerId requester, String name) {
        ensureOwner(requester);
        this.name = name;
    }

    public void replaceGraph(OwnerId requester, WorkflowGraph graph, Revision expected) {
        ensureOwner(requester);
        if (!revision.equals(expected)) {
            throw new CoreException(ErrorType.WORKFLOW_REVISION_CONFLICT,
                    Map.of("expectedRevision", expected.value(), "currentRevision", revision.value()));
        }

        this.graph = graph;

        this.validationStatus = ValidationStatus.NOT_VALIDATED;
        this.revision = revision.next();
    }

    public void markValidated() {
        this.validationStatus = ValidationStatus.VALID;
    }

    public void markInvalid() {
        this.validationStatus = ValidationStatus.INVALID;
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
