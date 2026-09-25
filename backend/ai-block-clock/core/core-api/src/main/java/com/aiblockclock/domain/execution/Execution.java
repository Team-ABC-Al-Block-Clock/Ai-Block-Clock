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
    private final ExecutionRequestId requestId;
    private final WorkflowId workflowId;
    private final UserId requester;
    private final RunId runId;
    private final ExecutionStatus status;
    private final int promptCount;
    private final ExecutionFile inputFile;

    // 돈을 먼저 떼고 엔진에 넘기므로, 접수한 순간에는 엔진이 준 실행 번호가 아직 없다.
    public static Execution requested(ExecutionId id, ExecutionRequestId requestId, WorkflowId workflowId,
                                      UserId requester, int promptCount, ExecutionFile inputFile) {
        return new Execution(id, requestId, workflowId, requester, null, ExecutionStatus.QUEUED,
                promptCount, inputFile);
    }

    public static Execution restore(ExecutionId id, ExecutionRequestId requestId, WorkflowId workflowId,
                                    UserId requester, RunId runId, ExecutionStatus status, int promptCount,
                                    ExecutionFile inputFile) {
        return new Execution(id, requestId, workflowId, requester, runId, status, promptCount, inputFile);
    }

    public boolean isRequestedBy(UserId candidate) {
        return requester.equals(candidate);
    }

    public boolean isDispatched() {
        return runId != null;
    }
}
