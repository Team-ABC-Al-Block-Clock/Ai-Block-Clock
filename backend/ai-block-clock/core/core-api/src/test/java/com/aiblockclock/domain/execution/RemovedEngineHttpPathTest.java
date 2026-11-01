package com.aiblockclock.domain.execution;

import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 실행 접수 · 결과 전달이 브로커로 옮겨 가며 없어진 길이다(ADR-0001). 남아 있으면 누군가 다시 쓴다.
// 엔진 HTTP(/artifact · /pdf)로 결과물을 받아 오던 수확이 없다는 것(B53)은 클래스 이름이 아니라 동작으로 본다 —
// 결과를 받을 때 엔진을 부르지 않는지는 ExecutionControllerTest 의 받기 테스트(B45)와 ExecutionResultCollectorTest 가 본다.
class RemovedEngineHttpPathTest {

    // 동시 실행 제한은 prefetch 가 한다. 넘치는 실행은 큐에서 기다리므로 "엔진이 꽉 찼다" 는 실패가 없다.
    @Test
    void 엔진이_꽉_찼다는_실패가_없다() {
        assertThat(ErrorType.values()).extracting(Enum::name).doesNotContain("EXECUTION_ENGINE_BUSY");
    }
}
