package com.aiblockclock.client.ai;

// 실행 요청의 종류다 (계약 v2.0 7.1). test 는 판매자 사전 테스트라 돌아가는 것은 같고 contribution 만 비어 온다.
public enum RunMode {
    RUN("run"),
    TEST("test");

    private final String wireName;

    RunMode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
