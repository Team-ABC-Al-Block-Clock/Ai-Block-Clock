package com.aiblockclock.client.ai;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// 실행 맡기기(SubmitRun)와 취소(CancelRun)를 브로커에 보낸다. 둘 다 publisher confirm(ack)을 받은 뒤에만 정상으로 돌아간다.
// nack · confirm 기한(ai.messaging.confirm-timeout) 초과 · 연결 실패는 AiClientException(REQUEST_FAILED) 이다.
// 브로커가 받았는지 모르는 채 실패로 알릴 수 있다. 다시 보내도 런타임이 실행 번호(message_id)로 두 번째를 거른다.
// 보내기 앞의 채널 열기 · 연결 맺기도 같은 기한에서 끊긴다(ai.yml 의 channel-rpc-timeout · connection-timeout).
public class RunQueuePublisher {
    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RunQueuePublisher(RabbitTemplate rabbitTemplate, Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    // abc.runs / submit
    public void submit(SubmitRun run) {
        publish(RunTopology.RUNS_EXCHANGE, RunTopology.SUBMIT_ROUTING_KEY, RunMessages.toMessage(run));
    }

    // abc.control (fanout). 런타임마다 제 큐로 받는다
    public void cancel(String executionId) {
        publish(RunTopology.CONTROL_EXCHANGE, "", RunMessages.toMessage(new CancelRun(executionId, Instant.now())));
    }

    private void publish(String exchange, String routingKey, Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        CorrelationData correlation = new CorrelationData(messageId);
        CorrelationData.Confirm confirm;

        try {
            rabbitTemplate.send(exchange, routingKey, message, correlation);
            confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (AmqpException | ExecutionException | TimeoutException exception) {
            throw new AiClientException(AiClientError.REQUEST_FAILED,
                    "publishing " + messageId + " to " + exchange + " failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new AiClientException(AiClientError.REQUEST_FAILED,
                    "publishing " + messageId + " to " + exchange + " was interrupted", exception);
        }

        if (!confirm.ack()) {
            throw new AiClientException(AiClientError.REQUEST_FAILED,
                    "broker did not accept " + messageId + ": " + confirm.reason());
        }
    }
}
