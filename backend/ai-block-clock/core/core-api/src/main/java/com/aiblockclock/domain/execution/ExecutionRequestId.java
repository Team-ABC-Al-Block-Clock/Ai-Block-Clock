package com.aiblockclock.domain.execution;

import java.util.UUID;

// 화면이 실행 버튼을 누를 때 만드는 번호다. 같은 번호가 다시 오면 새로 접수하지 않고 앞의 실행을 돌려준다.
public record ExecutionRequestId(UUID value) {
    public static ExecutionRequestId of(UUID value) {
        return new ExecutionRequestId(value);
    }
}
