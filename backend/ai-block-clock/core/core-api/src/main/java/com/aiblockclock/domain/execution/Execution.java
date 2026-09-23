package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.execution.ExecutionStatus;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Execution {
    private final ExecutionId id;
    private final WorkflowId workflowId;
    private final UserId requester;
    private final RunId runId;
    private final ExecutionStatus status;
    private final int promptCount;
    private final ExecutionFile inputFile;

    public static Execution started(ExecutionId id, WorkflowId workflowId, UserId requester,
                                    RunId runId, int promptCount, ExecutionFile inputFile) {
        return new Execution(id, workflowId, requester, runId, ExecutionStatus.QUEUED, promptCount, inputFile);
    }

    public static Execution restore(ExecutionId id, WorkflowId workflowId, UserId requester, RunId runId,
                                    ExecutionStatus status, int promptCount, ExecutionFile inputFile) {
        return new Execution(id, workflowId, requester, runId, status, promptCount, inputFile);
    }

    public boolean isRequestedBy(UserId candidate) {
        return requester.equals(candidate);
    }
}
