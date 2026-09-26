package com.aiblockclock.enums.node;

// 판매자 테스트 실행의 상태다. REQUESTED 는 엔진에 넘기기 전, RUNNING 은 엔진이 받은 뒤다.
// 엔진에 넘기지 못한 테스트는 기록을 지워, 하루 횟수를 쓰지 않은 것으로 둔다.
public enum NodeTestRunStatus {
    REQUESTED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED;
    }
}
