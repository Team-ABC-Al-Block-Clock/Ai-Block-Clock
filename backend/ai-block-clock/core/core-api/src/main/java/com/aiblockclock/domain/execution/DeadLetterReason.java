package com.aiblockclock.domain.execution;

import java.util.Arrays;

// abc.runs.dead 로 온 SubmitRun 이 왜 죽었는지다. 브로커가 붙인 x-first-death-reason 헤더로 가른다
// (ai/docs/integration-guide.md §4.8).
//   expired         큐에서 10분을 넘겨 기다렸다         → ABC_QUEUE_EXPIRED
//   delivery_limit  두 번 배달했는데도 끝내지 못했다    → ABC_RUNTIME_RETRY_EXHAUSTED
//   그 밖 · 없음                                         → ABC_RUNTIME_DEAD_LETTERED
// 어느 것이든 플랫폼 탓이다. 헤더 값은 브로커가 붙이는 철자 그대로만 맞춘다.
public enum DeadLetterReason {
    EXPIRED("expired", ExecutionProgress.QUEUE_EXPIRED),
    DELIVERY_LIMIT("delivery_limit", ExecutionProgress.RETRY_EXHAUSTED),
    OTHER(null, ExecutionProgress.DEAD_LETTERED);

    private final String header;
    private final String failureCode;

    DeadLetterReason(String header, String failureCode) {
        this.header = header;
        this.failureCode = failureCode;
    }

    public static DeadLetterReason of(String firstDeathReason) {
        return Arrays.stream(values())
                .filter(reason -> reason.header != null && reason.header.equals(firstDeathReason))
                .findFirst()
                .orElse(OTHER);
    }

    // dead letter 로 닫은 실행의 실패 코드인가다. 근거 백필이 닫은 까닭을 실패 코드로 되짚을 때 쓴다.
    public static boolean isFailureCodeOf(String failureCode) {
        return Arrays.stream(values()).anyMatch(reason -> reason.failureCode.equals(failureCode));
    }

    public String failureCode() {
        return failureCode;
    }
}
