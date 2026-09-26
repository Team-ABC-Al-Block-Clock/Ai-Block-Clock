package com.aiblockclock.api.batch;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

// @Scheduled 로 도는 정기 작업의 스레드다. 실행 조회(executionScheduler) · 충전 확인(chargeScheduler)과 나눈다.
// 여기서 정하지 않으면 스프링이 TaskScheduler 빈 중에서 골라 쓰는데, 무엇을 고를지가 빈이 몇 개인지에 따라 달라진다.
// 빈으로 두지 않는 것은 TaskScheduler 빈을 이름으로 주입받는 곳들에 후보를 하나 더 늘리지 않으려는 것이다.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class BatchSchedulerConfig implements SchedulingConfigurer, DisposableBean {
    private final ThreadPoolTaskScheduler batchScheduler = new ThreadPoolTaskScheduler();

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        batchScheduler.setThreadNamePrefix("batch-");
        batchScheduler.initialize();

        registrar.setTaskScheduler(batchScheduler);
    }

    @Override
    public void destroy() {
        batchScheduler.shutdown();
    }
}
