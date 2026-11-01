package com.aiblockclock.domain.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// interval 은 넘기지 않은 실행을 다시 발행해 보는 간격이다(기본 5초).
// abandonAfter 는 접수하고 이만큼 지나도록 넘기지 못한 실행을 버리는 한도다(기본 10분).
@ConfigurationProperties(prefix = "execution.dispatch")
public record ExecutionDispatchProperties(Duration interval, Duration abandonAfter) {
}
