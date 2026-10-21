package com.aiblockclock.api.batch;

import com.aiblockclock.domain.execution.ExecutionDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

// 커밋 직후 발행이 놓친 실행(브로커가 죽어 있었다 · 서버가 그 사이 내려갔다)을 execution.dispatch.interval(기본 5초)마다 메운다.
class ExecutionDispatchSchedulerTest {
    private final ExecutionDispatcher executionDispatcher = mock(ExecutionDispatcher.class);

    @Test
    void 차례가_오면_넘기지_않은_실행을_모두_발행해_본다() {
        new ExecutionDispatchScheduler(executionDispatcher).dispatch();

        then(executionDispatcher).should().dispatchPending();
    }

    @Test
    void 점검_주기는_execution_dispatch_interval_로_정한다() throws NoSuchMethodException {
        Method dispatch = ExecutionDispatchScheduler.class.getMethod("dispatch");
        Scheduled scheduled = dispatch.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString() + scheduled.fixedRateString()).contains("${execution.dispatch.interval");
    }
}
