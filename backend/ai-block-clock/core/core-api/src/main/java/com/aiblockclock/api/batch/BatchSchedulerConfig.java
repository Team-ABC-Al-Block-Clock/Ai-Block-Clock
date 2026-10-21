package com.aiblockclock.api.batch;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

// @Scheduled 로 도는 정기 작업(실행 발행 · 감시 점검 등)의 스레드들이다. 충전 확인(chargeScheduler)과 나눈다.
// 여기서 정하지 않으면 스프링이 TaskScheduler 빈 중에서 골라 쓰는데, 무엇을 고를지가 빈이 몇 개인지에 따라 달라진다.
// 빈으로 두지 않는 것은 TaskScheduler 빈을 이름으로 주입받는 곳들에 후보를 하나 더 늘리지 않으려는 것이다.
// 작업마다 스레드를 하나씩 둔다. 한 작업이 바깥(브로커 · 체인 · 엔진)을 기다리느라 붙잡혀도 다른 작업은 제 차례에 돌아야 한다.
// 응답하지 않는 브로커에는 발행 점검이 넘기지 않은 실행마다 confirm 기한씩 묶인다. 같은 작업이 겹쳐 돌지는 않는다 —
// fixedDelay · cron 은 앞 차례가 끝난 뒤에 다음 차례를 건다.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class BatchSchedulerConfig implements SchedulingConfigurer, DisposableBean {
    // 등급 재산정 · 원본 기록 · 실행 발행 점검 · 감시 점검 · 복구 · 근거 백필. 작업을 더하면 함께 늘린다.
    private static final int POOL_SIZE = 6;

    private final ThreadPoolTaskScheduler batchScheduler = new ThreadPoolTaskScheduler();

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        batchScheduler.setPoolSize(POOL_SIZE);
        batchScheduler.setThreadNamePrefix("batch-");
        batchScheduler.initialize();

        registrar.setTaskScheduler(batchScheduler);
    }

    @Override
    public void destroy() {
        batchScheduler.shutdown();
    }
}
