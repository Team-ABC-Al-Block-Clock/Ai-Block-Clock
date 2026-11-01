package com.aiblockclock.client.ai;

import java.util.Arrays;

// 실행 요청의 종류다 (계약 v2.0 7.1). test 는 판매자 사전 테스트라 돌아가는 것은 같고 contribution 만 비어 온다.
// 런타임은 SubmitRun 의 mode 를 RunEvent 에 그대로 돌려준다. 백엔드는 이 값으로 실행과 노드 테스트를 가른다.
public enum RunMode {
    RUN("run"),
    TEST("test");

    private final String wireName;

    RunMode(String wireName) {
        this.wireName = wireName;
    }

    // 모르는 값이면 어디로 보낼지 정할 수 없다. 읽지 못하는 메시지로 본다.
    public static RunMode from(String wireName) {
        return Arrays.stream(values())
                .filter(mode -> mode.wireName.equals(wireName))
                .findFirst()
                .orElseThrow(() -> new AiClientException(AiClientError.MALFORMED_RESPONSE,
                        "unknown run mode: " + wireName));
    }

    public String wireName() {
        return wireName;
    }
}
