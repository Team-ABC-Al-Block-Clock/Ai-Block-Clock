package com.aiblockclock.client.ai;

import java.util.Arrays;

// 실행 단위 상태는 queued · running · succeeded · failed 넷이다 (AI 파트 integration-guide.md).
// 일부만 돌고 끝난 실행도 이 넷 중 하나로 오고, 돌지 않은 노드는 not_executed 로 따로 온다.
// 모르는 값이 오면 끝난 실패로 본다 — 폴링이 끝나지 않는 것보다 낫다.
public enum RunState {
    QUEUED("queued"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    UNKNOWN(null);

    private final String wireName;

    RunState(String wireName) {
        this.wireName = wireName;
    }

    public static RunState from(String value) {
        return Arrays.stream(values())
                .filter(state -> state.wireName != null && state.wireName.equals(value))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED || this == UNKNOWN;
    }
}
