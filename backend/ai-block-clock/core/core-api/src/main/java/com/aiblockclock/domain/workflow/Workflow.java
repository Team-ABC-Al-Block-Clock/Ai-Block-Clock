package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
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
    private final UserId owner;

    private String name;
    private String description;
    private WorkflowGraph graph;
    private ValidationStatus validationStatus;
    private Revision revision;

    private final WorkflowTimestamps timestamps;

    public static Workflow create(
            WorkflowId id, UserId owner, String name, String description, WorkflowGraph graph) {
        return new Workflow(id, owner, name, description, graph,
                ValidationStatus.NOT_VALIDATED, Revision.initial(), WorkflowTimestamps.unsaved());
    }

    public static Workflow restore(
            WorkflowId id,
            UserId owner,
            String name,
            String description,
            WorkflowGraph graph,
            ValidationStatus validationStatus,
            Revision revision,
            WorkflowTimestamps timestamps
    ) {
        return new Workflow(id, owner, name, description, graph, validationStatus, revision, timestamps);
    }

    public void rename(UserId requester, String name) {
        ensureOwner(requester);
        this.name = name;
    }

    public void replaceGraph(UserId requester, WorkflowGraph graph, Revision expected) {
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

    public boolean isValidated() {
        return validationStatus == ValidationStatus.VALID;
    }

    public boolean isOwnedBy(UserId candidate) {
        return owner.equals(candidate);
    }

    public void ensureOwner(UserId requester) {
        if (!isOwnedBy(requester)) {
            throw new CoreException(ErrorType.WORKFLOW_FORBIDDEN);
        }
    }
}
