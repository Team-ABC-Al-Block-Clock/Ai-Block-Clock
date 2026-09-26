package com.aiblockclock.api.batch;

import com.aiblockclock.domain.execution.ExecutionPollingProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExecutionPollingProperties.class)
public class ExecutionSchedulerConfig {

    @Bean
    public TaskScheduler executionScheduler(ExecutionPollingProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(properties.workers());
        scheduler.setThreadNamePrefix("execution-poll-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);

        return scheduler;
    }
}
