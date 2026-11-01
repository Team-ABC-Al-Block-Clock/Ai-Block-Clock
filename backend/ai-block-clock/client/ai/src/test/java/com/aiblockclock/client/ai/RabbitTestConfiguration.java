package com.aiblockclock.client.ai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;

// 운영 compose 와 같은 이미지다(ADR-0001 Action Item 1). 컨텍스트마다 새 브로커가 뜬다.
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestConfiguration {

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer("rabbitmq:4.3.6-management");
    }
}
