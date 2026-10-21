package com.aiblockclock.api.messaging;

import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunMessages;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.RunTopology;
import com.aiblockclock.domain.execution.ExecutionService;
import com.aiblockclock.domain.execution.NodeTestRunService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

// abc.events.backend 의 진입점이다. 런타임이 보낸 RunEvent 를 mode 로 갈라 실행(run) · 노드 테스트(test)에 넘긴다.
//   읽을 수 없는 메시지(JSON 이 아님 · execution_id · type · attempt · seq 없음)는 다시 시도하지 않고 곧바로 거절한다 → DLQ
//   실행 번호가 UUID 가 아니면 모르는 실행이다. 다시 시도해도 알게 되지 않으니 경고만 남기고 정상 처리(ack)한다
//   처리 중 예외는 그대로 던진다. 리스너 재시도(ai.yml)가 다시 시도하고, 넘으면 거절한다 → DLQ
// DB 커밋 뒤에 돌아와야 ack 된다(ai/docs/integration-guide.md §4.7).
@Slf4j
@Component
@RequiredArgsConstructor
public class RunEventListener {
    private final ExecutionService executionService;
    private final NodeTestRunService nodeTestRunService;

    @RabbitListener(queues = RunTopology.EVENTS_QUEUE)
    public void onMessage(Message message) {
        RunEvent event = read(message);

        if (!isUuid(event.executionId())) {
            log.warn("실행 번호가 UUID 가 아니라 모르는 실행의 이벤트로 보고 넘어간다: execution={} mode={}",
                    event.executionId(), event.mode());

            return;
        }

        if (event.mode() == RunMode.TEST) {
            nodeTestRunService.applyEvent(event);

            return;
        }

        executionService.applyEvent(event);
    }

    private static RunEvent read(Message message) {
        try {
            return RunMessages.readEvent(message.getBody());
        } catch (AiClientException exception) {
            throw new AmqpRejectAndDontRequeueException("읽을 수 없는 실행 이벤트라 거절한다", exception);
        }
    }

    // 실행 · 테스트 실행 번호는 우리가 만든 UUID 다.
    private static boolean isUuid(String id) {
        try {
            UUID.fromString(id);

            return true;
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }
}
