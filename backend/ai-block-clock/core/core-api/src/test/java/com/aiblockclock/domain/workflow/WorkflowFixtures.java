package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.Timestamps;
import com.aiblockclock.enums.workflow.ValidationStatus;

public final class WorkflowFixtures {
    private WorkflowFixtures() {
    }

    public static Workflow validated(OwnerId owner, WorkflowGraph graph) {
        return Workflow.restore(WorkflowId.newId(), owner, "성적 분석", null, graph,
                ValidationStatus.VALID, Revision.initial(), Timestamps.unsaved(), null);
    }
}
