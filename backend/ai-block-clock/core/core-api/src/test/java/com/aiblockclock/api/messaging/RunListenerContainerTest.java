package com.aiblockclock.api.messaging;

import com.aiblockclock.domain.execution.DeadLetterReason;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionService;
import com.aiblockclock.domain.execution.NodeTestRunService;
import com.aiblockclock.domain.node.NodeTestRunId;
import com.aiblockclock.testsupport.RabbitTestConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;

// 실제 RabbitMQ 위에서 리스너가 붙어 있는지, 처리에 실패한 이벤트가 재시도 뒤 DLQ 로 가는지,
// 큐에서 죽은 실행이 사유와 함께 닫히러 가는지 본다. 도메인은 목으로 바꿔 무엇이 몇 번 불렸는지만 본다.
@Tag("container")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=true")
@Import(RabbitTestConfiguration.class)
class RunListenerContainerTest {
    private static final List<String> QUEUES = List.of(
            "abc.runs.submit", "abc.runs.dead", "abc.events.backend", "abc.events.backend.dlq");

    @MockitoBean
    private ExecutionService executionService;

    @MockitoBean
    private NodeTestRunService nodeTestRunService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @BeforeEach
    void 계약의_큐가_선언되어_있고_비어_있다() {
        for (String queue : QUEUES) {
            assertThat(amqpAdmin.getQueueInfo(queue)).as("백엔드가 %s 큐를 선언하지 않았다", queue).isNotNull();
            amqpAdmin.purgeQueue(queue);
        }
    }

    @Test
    void 런타임이_보낸_실행_이벤트를_받아_실행에_적용하고_ack_한다() {
        String executionId = UUID.randomUUID().toString();

        rabbitTemplate.send("abc.events", "run.progress", event(executionId, "run"));

        then(executionService).should(timeout(10_000))
                .applyEvent(argThat(event -> event.executionId().equals(executionId)));
        assertThat(rabbitTemplate.receive("abc.events.backend.dlq", 2_000)).isNull();
    }

    @Test
    void 노드_테스트의_이벤트는_노드_테스트에_적용한다() {
        String testRunId = UUID.randomUUID().toString();

        rabbitTemplate.send("abc.events", "run.succeeded", event(testRunId, "test"));

        then(nodeTestRunService).should(timeout(10_000))
                .applyEvent(argThat(event -> event.executionId().equals(testRunId)));
    }

    // 처음 한 번에 세 번 더 시도한다(ai.yml). 그래도 실패하면 다시 넣지 않고 거절해 DLQ 로 보낸다. 실행은 감시가 닫는다.
    @Test
    void 처리에_실패한_이벤트는_세_번_더_시도한_뒤_DLQ_로_간다() {
        willThrow(new DataAccessResourceFailureException("db down")).given(executionService).applyEvent(any());

        rabbitTemplate.send("abc.events", "run.progress", event(UUID.randomUUID().toString(), "run"));

        Message dead = rabbitTemplate.receive("abc.events.backend.dlq", 30_000);
        assertThat(dead).isNotNull();
        then(executionService).should(after(2_000).times(4)).applyEvent(any());
    }

    @Test
    void 읽을_수_없는_이벤트는_처리하지_않고_DLQ_로_보낸다() {
        rabbitTemplate.send("abc.events", "run.progress",
                new Message("<html>502</html>".getBytes(StandardCharsets.UTF_8), new MessageProperties()));

        Message dead = rabbitTemplate.receive("abc.events.backend.dlq", 30_000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("<html>502</html>");
        then(executionService).shouldHaveNoInteractions();
        then(nodeTestRunService).shouldHaveNoInteractions();
    }

    // 런타임이 붙잡기 전에 큐에서 오래 기다린 실행이다. 큐 TTL(10분)을 기다릴 수 없어 메시지 만료로 같은 길을 태운다.
    @Test
    void 큐에서_만료된_실행은_만료_사유와_함께_닫으러_간다() {
        String executionId = UUID.randomUUID().toString();
        MessageProperties properties = new MessageProperties();
        properties.setExpiration("100");

        rabbitTemplate.send("abc.runs", "submit", new Message(submitBody(executionId, "run"), properties));

        then(executionService).should(timeout(20_000))
                .closeDeadLettered(ExecutionId.of(UUID.fromString(executionId)), DeadLetterReason.EXPIRED);
    }

    @Test
    void 큐에서_만료된_노드_테스트도_만료_사유와_함께_닫으러_간다() {
        String testRunId = UUID.randomUUID().toString();
        MessageProperties properties = new MessageProperties();
        properties.setExpiration("100");

        rabbitTemplate.send("abc.runs", "submit", new Message(submitBody(testRunId, "test"), properties));

        then(nodeTestRunService).should(timeout(20_000))
                .closeDeadLettered(NodeTestRunId.of(UUID.fromString(testRunId)), DeadLetterReason.EXPIRED);
    }

    private static Message event(String executionId, String mode) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setType("RunEvent");
        properties.setMessageId(executionId + ":1:2");

        String body = """
                {"execution_id":"%s","mode":"%s","attempt":1,"seq":2,"type":"progress",
                 "occurred_at":"2026-10-16T05:00:03Z",
                 "snapshot":{"run_id":"%s","status":"running","progress":{"done":0,"total":2,"percent":0},"nodes":[]},
                 "artifact":null,"result_files":null}""".formatted(executionId, mode, executionId);

        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    private static byte[] submitBody(String executionId, String mode) {
        return """
                {"execution_id":"%s","mode":"%s","workflow":{"id":"w1","nodes":[]},"input":null,"comment":null,
                 "mock":true,"files":[],"ref_files":{},"submitted_at":"2026-10-16T05:00:00Z"}"""
                .formatted(executionId, mode).getBytes(StandardCharsets.UTF_8);
    }
}
