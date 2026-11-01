package com.aiblockclock.domain.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// 감시 기한 = 단계 수 × perStep + grace. 키 이름은 폴링 시절의 execution.polling.per-step · grace 그대로다.
// 실행은 마지막 이벤트에서, 노드 테스트는 접수한 시각에서 잰다. 기한이 지나야 엔진에 HTTP 로 한 번 묻는다.
@ConfigurationProperties(prefix = "execution.polling")
public record ExecutionWatchProperties(Duration perStep, Duration grace) {

    // 단계가 없는 실행도 grace 만으로 끊지 않도록 한 단계로 센다.
    public Duration deadline(int stepCount) {
        return perStep.multipliedBy(Math.max(stepCount, 1)).plus(grace);
    }
}
