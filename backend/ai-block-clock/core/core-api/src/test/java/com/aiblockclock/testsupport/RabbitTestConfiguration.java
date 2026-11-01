package com.aiblockclock.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;

// 실제 RabbitMQ 가 있어야 보이는 것(리스너 재시도 뒤 DLQ, dead letter 사유 헤더)을 볼 때 붙인다. 운영 compose 와 같은 이미지다.
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestConfiguration {

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer("rabbitmq:4.3.6-management");
    }
}
