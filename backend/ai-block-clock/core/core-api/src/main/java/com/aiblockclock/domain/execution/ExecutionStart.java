package com.aiblockclock.domain.execution;

// 실행 시작의 결과다. 같은 요청 번호로 다시 온 요청이면 앞서 접수한 실행을 돌려주므로,
// 이번에 새로 만든 실행인지를 함께 알려 이미 따라가고 있거나 끝난 실행을 다시 따라가지 않게 한다.
public record ExecutionStart(Execution execution, boolean created) {
    public static ExecutionStart newlyCreated(Execution execution) {
        return new ExecutionStart(execution, true);
    }

    public static ExecutionStart alreadyRequested(Execution execution) {
        return new ExecutionStart(execution, false);
    }
}
