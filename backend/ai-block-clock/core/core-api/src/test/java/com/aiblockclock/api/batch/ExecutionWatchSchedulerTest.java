package com.aiblockclock.api.batch;

import com.aiblockclock.domain.execution.ExecutionTracker;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

// 감시 점검은 execution.watch.interval(기본 10초)마다 돈다. 실행마다 2초 사슬을 거는 폴링을 대신한다.
class ExecutionWatchSchedulerTest {
    private final ExecutionTracker executionTracker = mock(ExecutionTracker.class);

    @Test
    void 차례가_오면_감시_중인_실행의_기한을_점검한다() {
        new ExecutionWatchScheduler(executionTracker).check();

        then(executionTracker).should().check();
    }

    @Test
    void 점검_주기는_execution_watch_interval_로_정한다() throws NoSuchMethodException {
        Method check = ExecutionWatchScheduler.class.getMethod("check");
        Scheduled scheduled = check.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString() + scheduled.fixedRateString()).contains("${execution.watch.interval");
    }
}
