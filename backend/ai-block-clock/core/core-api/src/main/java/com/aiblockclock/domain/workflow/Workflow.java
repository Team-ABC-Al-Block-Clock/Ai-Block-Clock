package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.Timestamps;
import com.aiblockclock.enums.workflow.ValidationStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

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

    public boolean isValidated() {
        return validationStatus == ValidationStatus.VALID;
    }
}
