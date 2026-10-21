package com.aiblockclock.api.messaging;

import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.RunMessages;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.RunTopology;
import com.aiblockclock.client.ai.SubmitRun;
import com.aiblockclock.domain.execution.DeadLetterReason;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionService;
import com.aiblockclock.domain.execution.NodeTestRunService;
import com.aiblockclock.domain.node.NodeTestRunId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

// abc.runs.dead 의 진입점이다. 본문은 dead letter 된 SubmitRun 그대로이고, 브로커가 붙인 x-first-death-reason 헤더로
// 왜 죽었는지 안다 (ai/docs/integration-guide.md §4.8). mode 로 갈라 실행 · 노드 테스트를 플랫폼 탓으로 닫는다.
// SubmitRun 으로 읽히지 않는 본문은 경고만 남기고 버린다(ack). 다시 시도해도 읽히지 않고, 붙잡고 있으면 뒤의 것까지 막힌다.
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadRunListener {
    private static final String FIRST_DEATH_REASON = "x-first-death-reason";

    private final ExecutionService executionService;
    private final NodeTestRunService nodeTestRunService;

    @RabbitListener(queues = RunTopology.DEAD_QUEUE)
    public void onMessage(Message message) {
        SubmitRun dead;
        UUID id;

        try {
            dead = RunMessages.readSubmit(message.getBody());
            id = UUID.fromString(dead.executionId());
        } catch (AiClientException | IllegalArgumentException exception) {
            log.warn("dead letter 로 온 본문을 SubmitRun 으로 읽지 못해 버린다: messageId={}",
                    message.getMessageProperties().getMessageId(), exception);

            return;
        }

        DeadLetterReason reason = DeadLetterReason.of(
                Objects.toString(message.getMessageProperties().getHeader(FIRST_DEATH_REASON), null));

        if (dead.mode() == RunMode.TEST) {
            nodeTestRunService.closeDeadLettered(NodeTestRunId.of(id), reason);

            return;
        }

        executionService.closeDeadLettered(ExecutionId.of(id), reason);
    }
}
