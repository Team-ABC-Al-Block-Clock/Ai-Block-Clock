package com.aiblockclock.api.batch;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

// @Scheduled 로 도는 정기 작업의 스레드다. 따로 정하지 않으면 스프링이 하나뿐인 TaskScheduler 빈인
// 실행 추적용 executionScheduler 를 가져다 써서, 정기 작업이 실행 폴링 스레드를 차지한다. 그래서 여기서 나눠 둔다.
// 빈으로 두지 않는 것도 같은 이유다. TaskScheduler 빈이 둘이 되면 executionScheduler 를 타입으로 주입받는 곳이 흔들린다.
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
