package com.aiblockclock.api.messaging;

import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunEventType;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.domain.execution.ExecutionService;
import com.aiblockclock.domain.execution.NodeTestRunService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

// abc.events.backend 의 진입점이다. 실행 · 노드 테스트로 가르고, 읽을 수 없는 메시지는 다시 시도하지 않고 거절한다.
class RunEventListenerTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String EXECUTION_ID = "6f1c2a9e-0000-4000-8000-000000000001";

    private final ExecutionService executionService = mock(ExecutionService.class);
    private final NodeTestRunService nodeTestRunService = mock(NodeTestRunService.class);
    private final RunEventListener sut = new RunEventListener(executionService, nodeTestRunService);

    @Test
    void 실행의_이벤트는_실행에_적용한다() {
        sut.onMessage(message(event("run")));

        ArgumentCaptor<RunEvent> applied = ArgumentCaptor.forClass(RunEvent.class);
        then(executionService).should().applyEvent(applied.capture());
        assertThat(applied.getValue().executionId()).isEqualTo(EXECUTION_ID);
        assertThat(applied.getValue().mode()).isEqualTo(RunMode.RUN);
        assertThat(applied.getValue().type()).isEqualTo(RunEventType.PROGRESS);
        assertThat(applied.getValue().attempt()).isEqualTo(1);
        assertThat(applied.getValue().seq()).isEqualTo(2);
        then(nodeTestRunService).shouldHaveNoInteractions();
    }

    @Test
    void 노드_테스트의_이벤트는_노드_테스트에_적용한다() {
        sut.onMessage(message(event("test")));

        ArgumentCaptor<RunEvent> applied = ArgumentCaptor.forClass(RunEvent.class);
        then(nodeTestRunService).should().applyEvent(applied.capture());
        assertThat(applied.getValue().mode()).isEqualTo(RunMode.TEST);
        then(executionService).shouldHaveNoInteractions();
    }

    @Test
    void JSON_이_아닌_메시지는_다시_시도하지_않도록_거절한다() {
        Message broken = new Message("<html>502</html>".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        assertThatThrownBy(() -> sut.onMessage(broken)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        then(executionService).shouldHaveNoInteractions();
        then(nodeTestRunService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"execution_id", "type", "attempt", "seq"})
    void 꼭_있어야_할_칸이_빠진_메시지는_다시_시도하지_않도록_거절한다(String missing) {
        ObjectNode event = event("run");
        event.remove(missing);

        assertThatThrownBy(() -> sut.onMessage(message(event))).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        then(executionService).shouldHaveNoInteractions();
    }

    // 계약에 없는 type 이면 상태를, 없는 mode 면 실행 · 노드 테스트 어디로 보낼지를 정할 수 없다.
    @ParameterizedTest
    @CsvSource({"type, paused", "mode, batch"})
    void 계약에_없는_값이_온_메시지는_다시_시도하지_않도록_거절한다(String field, String unknown) {
        ObjectNode event = event("run");
        event.put(field, unknown);

        assertThatThrownBy(() -> sut.onMessage(message(event))).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        then(executionService).shouldHaveNoInteractions();
        then(nodeTestRunService).shouldHaveNoInteractions();
    }

    // 우리 실행 번호는 UUID 다. UUID 가 아닌 번호는 모르는 실행이라 경고만 남기고 정상 처리(ack)한다(B21).
    // 읽을 수 없는 메시지(B22)가 아니므로 거절해 DLQ 로 보내지도 않는다.
    @Test
    void 실행_번호가_UUID_가_아닌_실행_이벤트는_모르는_실행으로_보고_던지지_않는다() {
        ObjectNode event = event("run");
        event.put("execution_id", "not-a-uuid");

        assertThatCode(() -> sut.onMessage(message(event))).doesNotThrowAnyException();

        then(executionService).shouldHaveNoInteractions();
        then(nodeTestRunService).shouldHaveNoInteractions();
    }

    @Test
    void 테스트_실행_번호가_UUID_가_아닌_노드_테스트_이벤트는_모르는_테스트로_보고_던지지_않는다() {
        ObjectNode event = event("test");
        event.put("execution_id", "not-a-uuid");

        assertThatCode(() -> sut.onMessage(message(event))).doesNotThrowAnyException();

        then(executionService).shouldHaveNoInteractions();
        then(nodeTestRunService).shouldHaveNoInteractions();
    }

    // 리스너 재시도(ai.yml)가 다시 시도하게 그대로 던진다. 거절로 바꾸면 한 번에 DLQ 로 가 버린다.
    @Test
    void 처리_중_예외는_그대로_던져_다시_시도하게_한다() {
        DataAccessResourceFailureException down = new DataAccessResourceFailureException("db down");
        willThrow(down).given(executionService).applyEvent(any());

        assertThatThrownBy(() -> sut.onMessage(message(event("run"))))
                .isSameAs(down)
                .isNotInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private static ObjectNode event(String mode) {
        return (ObjectNode) MAPPER.readTree("""
                {"execution_id":"%s","mode":"%s","attempt":1,"seq":2,"type":"progress",
                 "occurred_at":"2026-10-16T05:00:03Z",
                 "snapshot":{"run_id":"%s","status":"running","progress":{"done":0,"total":2,"percent":0},"nodes":[]},
                 "artifact":null,"result_files":null}""".formatted(EXECUTION_ID, mode, EXECUTION_ID));
    }

    private static Message message(ObjectNode event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setType("RunEvent");

        return new Message(MAPPER.writeValueAsBytes(event), properties);
    }
}
