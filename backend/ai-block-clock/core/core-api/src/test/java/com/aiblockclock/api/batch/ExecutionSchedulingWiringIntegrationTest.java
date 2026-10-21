package com.aiblockclock.api.batch;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// 실행을 2초마다 엔진에 묻던 폴링 사슬(execution-poll 스레드 풀)은 없어지고, 주기 작업은 발행 점검과 감시 점검 둘만 남는다.
@Tag("integration")
@SpringBootTest
class ExecutionSchedulingWiringIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void 실행마다_2초_폴링을_걸던_스레드_풀이_없다() {
        assertThat(context.containsBean("executionScheduler")).isFalse();
    }

    @Test
    void 넘기지_않은_실행의_발행_점검과_감시_점검이_주기_작업으로_떠_있다() {
        assertThat(context.getBeansOfType(ExecutionDispatchScheduler.class)).hasSize(1);
        assertThat(context.getBeansOfType(ExecutionWatchScheduler.class)).hasSize(1);
    }
}
