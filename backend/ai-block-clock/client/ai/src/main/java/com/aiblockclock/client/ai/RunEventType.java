package com.aiblockclock.client.ai;

import java.util.Arrays;
import java.util.Locale;

// RunEvent 의 type 이다 (ai/docs/integration-guide.md §4.4). 백엔드는 실행 상태를 snapshot.status 가 아니라 이 값으로 정한다.
//   accepted · progress → RUNNING, succeeded → SUCCEEDED, failed · rejected → FAILED
public enum RunEventType {
    ACCEPTED,
    PROGRESS,
    SUCCEEDED,
    FAILED,
    REJECTED;

    // 모르는 값이면 실행을 어느 상태로 둘지 정할 수 없다. 읽지 못하는 메시지로 본다.
    public static RunEventType from(String wireName) {
        return Arrays.stream(values())
                .filter(type -> type.wireName().equals(wireName))
                .findFirst()
                .orElseThrow(() -> new AiClientException(AiClientError.MALFORMED_RESPONSE,
                        "unknown run event type: " + wireName));
    }

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
