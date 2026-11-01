package com.aiblockclock.domain.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// 복구 스케줄러의 주기에 함께 도는 큐 대기 안전망의 설정이다.
// queueGrace 는 넘긴 뒤 큐 대기 한도(abc.runs.submit 의 TTL 10분)에 더해 dead letter 를 기다려 주는 유예다(기본 5분).
// 넘긴 지 한도 + 유예가 지나도록 QUEUED 인 실행은 dead letter 를 잃은 것으로 보고 안전망이 닫는다.
@ConfigurationProperties(prefix = "execution.recovery")
public record ExecutionRecoveryProperties(Duration queueGrace) {
}
