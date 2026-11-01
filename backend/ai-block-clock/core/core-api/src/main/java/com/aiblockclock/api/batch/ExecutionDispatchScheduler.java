package com.aiblockclock.api.batch;

import com.aiblockclock.domain.execution.ExecutionDispatcher;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 넘기지 않은 실행의 발행을 execution.dispatch.interval(기본 5초)마다 다시 시도한다. 커밋 직후 발행이 놓친 것
// (브로커가 죽어 있었다 · 서버가 그 사이 내려갔다)을 메운다. 기동 전에 남아 있던 것은 첫 차례에 넘긴다.
@Component
@RequiredArgsConstructor
public class ExecutionDispatchScheduler {
    private final ExecutionDispatcher executionDispatcher;

    @Scheduled(fixedDelayString = "${execution.dispatch.interval}",
            initialDelayString = "${execution.dispatch.interval}")
    public void dispatch() {
        executionDispatcher.dispatchPending();
    }
}
