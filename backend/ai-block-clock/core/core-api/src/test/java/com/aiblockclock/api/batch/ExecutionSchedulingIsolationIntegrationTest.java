package com.aiblockclock.api.batch;

import com.aiblockclock.domain.execution.ExecutionDispatcher;
import com.aiblockclock.domain.execution.ExecutionTracker;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;

// B46 보강 — 브로커가 연결은 받고 답하지 않으면 발행 점검이 한동안 붙잡힌다(E2E 버그 2 의 batch- 스레드).
// 그동안에도 감시 같은 다른 주기 작업은 제 차례에 돌아야 한다. 어떻게 떼어 놓을지(스레드 수 · 스케줄러 · 넘기기)는 묻지 않는다.
// 발행 점검은 들어가면 풀어 줄 때까지 돌아오지 않게 해 두고, 그동안 감시 점검이 불리는지만 본다.
// 두 주기를 짧게(200ms) 잡으려고 테스트 설정(1h)을 이 클래스에서만 덮어쓴다.
@Tag("integration")
@SpringBootTest(properties = {"execution.dispatch.interval=200ms", "execution.watch.interval=200ms"})
@Import(ExecutionSchedulingIsolationIntegrationTest.StuckDispatcherConfiguration.class)
class ExecutionSchedulingIsolationIntegrationTest {
    private static final CountDownLatch DISPATCH_ENTERED = new CountDownLatch(1);
    private static final CountDownLatch DISPATCH_RELEASED = new CountDownLatch(1);

    @MockitoBean
    private ExecutionTracker executionTracker;

    @Test
    void 발행_점검이_브로커에_붙잡혀_있어도_감시_점검은_제_차례에_돈다() throws InterruptedException {
        try {
            assertThat(DISPATCH_ENTERED.await(10, TimeUnit.SECONDS)).as("발행 점검이 돌지 않았다").isTrue();
            clearInvocations(executionTracker);

            then(executionTracker).should(timeout(5_000).atLeast(3)).check();
        } finally {
            DISPATCH_RELEASED.countDown();
        }
    }

    // 발행 점검(dispatchPending)은 들어가면 풀어 줄 때까지 돌아오지 않는다. 그 밖의 호출은 아무것도 하지 않는다.
    // 컨텍스트가 뜰 때부터 막혀 있어야 해서 @MockitoBean 대신 처음부터 그렇게 만든 목을 앞세운다.
    @TestConfiguration(proxyBeanMethods = false)
    static class StuckDispatcherConfiguration {

        @Bean
        @Primary
        ExecutionDispatcher stuckExecutionDispatcher() {
            return mock(ExecutionDispatcher.class, invocation -> {
                if (invocation.getMethod().getName().equals("dispatchPending")) {
                    DISPATCH_ENTERED.countDown();
                    DISPATCH_RELEASED.await();
                }

                return RETURNS_DEFAULTS.answer(invocation);
            });
        }
    }
}
