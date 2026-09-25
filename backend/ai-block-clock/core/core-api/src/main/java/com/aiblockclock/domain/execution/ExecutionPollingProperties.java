package com.aiblockclock.domain.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "execution.polling")
public record ExecutionPollingProperties(
        int workers,
        Duration interval,
        Duration perStep,
        Duration grace
) {
    public ExecutionPollingProperties {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("execution.polling.interval 은 0보다 커야 한다");
        }
    }

    public Duration deadline(int stepCount) {
        return perStep.multipliedBy(Math.max(stepCount, 1)).plus(grace);
    }
}
