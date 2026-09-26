package com.aiblockclock.api.batch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 충전 재확인 · 실행 진행 조회와 스레드를 나눈다. 지급이 체인을 기다리는 동안 다른 차례가 밀리지 않는다.
// 보관 지갑은 한 번에 한 건만 보내므로 스레드는 하나다. 둘이면 같은 거래 번호로 서명하게 된다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "payment.withdrawal.payout", name = "enabled", havingValue = "true")
public class WithdrawalPayoutSchedulerConfig {

    @Bean
    public TaskScheduler payoutScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("withdrawal-payout-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);

        return scheduler;
    }
}
