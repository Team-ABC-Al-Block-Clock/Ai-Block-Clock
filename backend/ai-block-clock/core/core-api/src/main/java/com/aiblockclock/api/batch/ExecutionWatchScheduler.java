package com.aiblockclock.api.batch;

import com.aiblockclock.domain.execution.ExecutionTracker;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 감시 점검을 execution.watch.interval(기본 10초)마다 돌린다. 이벤트가 끊긴 채 기한이 지난 실행만 엔진에 묻는다.
// 실행마다 2초 간격으로 엔진을 묻던 폴링을 대신한다.
@Component
@RequiredArgsConstructor
public class ExecutionWatchScheduler {
    private final ExecutionTracker executionTracker;

    @Scheduled(fixedDelayString = "${execution.watch.interval}", initialDelayString = "${execution.watch.interval}")
    public void check() {
        executionTracker.check();
    }
}
