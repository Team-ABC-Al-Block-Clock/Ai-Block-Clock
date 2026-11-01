package com.aiblockclock.client.ai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

// 브로커가 메모리 경보로 발행을 막으면 confirm 이 오지 않는다. 기한(ai.messaging.confirm-timeout)에서 끊고 실패로 알린다.
// 경보를 건 브로커는 다른 테스트를 막으므로 이 클래스만 제 브로커를 띄운다.
@Tag("container")
@SpringBootTest(properties = {"spring.config.import=classpath:ai.yml", "ai.messaging.confirm-timeout=1s"})
@Import(RabbitTestConfiguration.class)
class RunQueueConfirmTimeoutContainerTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private RunQueuePublisher runQueuePublisher;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitMQContainer rabbit;

    @Test
    void confirm_이_기한_안에_오지_않으면_기다리지_않고_REQUEST_FAILED_다() throws Exception {
        assertThat(amqpAdmin.getQueueInfo("abc.runs.submit")).as("백엔드가 abc.runs.submit 큐를 선언하지 않았다").isNotNull();
        amqpAdmin.purgeQueue("abc.runs.submit");
        rabbit.execInContainer("rabbitmqctl", "set_vm_memory_high_watermark", "0");

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                    assertThatThrownBy(() -> runQueuePublisher.submit(submit()))
                            .isInstanceOfSatisfying(AiClientException.class, exception ->
                                    assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED)));
        } finally {
            rabbit.execInContainer("rabbitmqctl", "set_vm_memory_high_watermark", "0.4");
        }
    }

    private static SubmitRun submit() {
        return new SubmitRun(UUID.randomUUID().toString(), RunMode.RUN, MAPPER.readTree("{\"id\":\"w1\",\"nodes\":[]}"),
                null, null, true, List.of(), Map.of(), Instant.parse("2026-10-16T05:00:00Z"));
    }
}
