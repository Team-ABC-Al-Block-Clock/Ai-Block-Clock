package com.aiblockclock.api.batch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 정기 작업(batch-)과 스레드를 나눈다. 체인이 느려져도 실행 발행 · 감시 점검이 밀리지 않는다.
// 한 번에 한 차례만 돌면 되므로 스레드는 하나다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "payment.charge", name = "polling-enabled", havingValue = "true")
public class ChargeSchedulerConfig {

    @Bean
    public TaskScheduler chargeScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("charge-poll-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);

        return scheduler;
    }
}
