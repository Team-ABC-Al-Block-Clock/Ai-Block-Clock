package com.aiblockclock.client.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

// 발행은 브로커의 confirm(ack)을 받은 뒤에만 정상으로 돌아간다(B46 · B47). 나머지는 모두 REQUEST_FAILED 다.
// 브로커는 RabbitTemplate 목이다. confirm 은 send 에 넘긴 CorrelationData 의 future 를 채워 흉내 낸다.
class RunQueuePublisherTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String EXECUTION_ID = "6f1c2a9e-0000-4000-8000-000000000001";

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RunQueuePublisher sut = new RunQueuePublisher(rabbitTemplate, Duration.ofMillis(50));

    @Test
    void 실행_맡기기는_abc_runs_의_submit_으로_실행_번호를_달고_보내고_ack_를_받으면_돌아온다() {
        brokerConfirms(new CorrelationData.Confirm(true, null));

        assertThatCode(() -> sut.submit(submit())).doesNotThrowAnyException();

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        then(rabbitTemplate).should().send(eq("abc.runs"), eq("submit"), message.capture(), correlation.capture());
        assertThat(message.getValue().getMessageProperties().getType()).isEqualTo("SubmitRun");
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo(EXECUTION_ID);
        assertThat(MAPPER.readTree(message.getValue().getBody()))
                .isEqualTo(MAPPER.readTree(RunMessages.toMessage(submit()).getBody()));
        assertThat(correlation.getValue().getId()).isEqualTo(EXECUTION_ID);
    }

    @Test
    void confirm_이_nack_이면_REQUEST_FAILED_다() {
        brokerConfirms(new CorrelationData.Confirm(false, "nack"));

        assertThatThrownBy(() -> sut.submit(submit()))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED));
    }

    @Test
    void confirm_이_기한_안에_오지_않으면_REQUEST_FAILED_다() {
        assertThatThrownBy(() -> sut.submit(submit()))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED));
    }

    @Test
    void 브로커에_닿지_못하면_REQUEST_FAILED_다() {
        willThrow(new AmqpConnectException(new ConnectException("connection refused")))
                .given(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> sut.submit(submit()))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED));
    }

    @Test
    void 취소는_abc_control_로_취소_번호를_달고_보내고_ack_를_받으면_돌아온다() {
        brokerConfirms(new CorrelationData.Confirm(true, null));

        assertThatCode(() -> sut.cancel("x")).doesNotThrowAnyException();

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        then(rabbitTemplate).should().send(eq("abc.control"), eq(""), message.capture(), correlation.capture());
        JsonNode body = MAPPER.readTree(message.getValue().getBody());
        assertThat(message.getValue().getMessageProperties().getType()).isEqualTo("CancelRun");
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo("cancel:x");
        assertThat(body.get("execution_id").asString()).isEqualTo("x");
        assertThat(correlation.getValue().getId()).isEqualTo("cancel:x");
    }

    @Test
    void 취소도_confirm_이_nack_이면_REQUEST_FAILED_다() {
        brokerConfirms(new CorrelationData.Confirm(false, "nack"));

        assertThatThrownBy(() -> sut.cancel("x"))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED));
    }

    // confirm 을 기다리는 스레드가 끊기면 실패로 알리되, 끊으라는 요청은 부른 쪽이 볼 수 있게 남겨 둔다.
    @Test
    void confirm_을_기다리다_끊기면_REQUEST_FAILED_이고_끊김_표시를_남겨_둔다() {
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> sut.submit(submit()))
                    .isInstanceOfSatisfying(AiClientException.class, exception ->
                            assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private void brokerConfirms(CorrelationData.Confirm confirm) {
        willAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(confirm);

            return null;
        }).given(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private static SubmitRun submit() {
        return new SubmitRun(EXECUTION_ID, RunMode.RUN, MAPPER.readTree("{\"id\":\"w1\",\"nodes\":[]}"), null, null,
                false, List.of(), Map.of(), Instant.parse("2026-10-16T05:00:00Z"));
    }
}
