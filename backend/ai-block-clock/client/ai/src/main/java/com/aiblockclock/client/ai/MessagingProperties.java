package com.aiblockclock.client.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// confirmTimeout 은 발행한 뒤 브로커의 confirm(ack)을 기다리는 한도다. 넘기면 발행 실패로 본다.
@ConfigurationProperties(prefix = "ai.messaging")
public record MessagingProperties(Duration confirmTimeout) {
}
