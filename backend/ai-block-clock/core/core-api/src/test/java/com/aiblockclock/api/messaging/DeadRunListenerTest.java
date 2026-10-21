package com.aiblockclock.api.messaging;

import com.aiblockclock.domain.execution.DeadLetterReason;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionService;
import com.aiblockclock.domain.execution.NodeTestRunService;
import com.aiblockclock.domain.node.NodeTestRunId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

// abc.runs.dead 의 진입점이다. 본문은 죽은 SubmitRun 그대로이고, 사유는 브로커가 붙인 x-first-death-reason 헤더에 있다.
class DeadRunListenerTest {
    private static final String EXECUTION_ID = "6f1c2a9e-0000-4000-8000-000000000001";

    private final ExecutionService executionService = mock(ExecutionService.class);
    private final NodeTestRunService nodeTestRunService = mock(NodeTestRunService.class);
    private final DeadRunListener sut = new DeadRunListener(executionService, nodeTestRunService);

    @ParameterizedTest
    @CsvSource({"expired, EXPIRED", "delivery_limit, DELIVERY_LIMIT", "rejected, OTHER"})
    void 죽은_실행은_헤더의_사유와_함께_닫게_한다(String header, DeadLetterReason reason) {
        sut.onMessage(deadLetter("run", header));

        then(executionService).should().closeDeadLettered(ExecutionId.of(UUID.fromString(EXECUTION_ID)), reason);
        then(nodeTestRunService).shouldHaveNoInteractions();
    }

    @Test
    void 사유_헤더가_없으면_그_밖의_사유로_닫게_한다() {
        sut.onMessage(deadLetter("run", null));

        then(executionService).should()
                .closeDeadLettered(ExecutionId.of(UUID.fromString(EXECUTION_ID)), DeadLetterReason.OTHER);
    }

    @Test
    void 죽은_노드_테스트는_노드_테스트로_닫게_한다() {
        sut.onMessage(deadLetter("test", "expired"));

        then(nodeTestRunService).should()
                .closeDeadLettered(NodeTestRunId.of(UUID.fromString(EXECUTION_ID)), DeadLetterReason.EXPIRED);
        then(executionService).shouldHaveNoInteractions();
    }

    // 다시 시도해도 읽히지 않는다. 붙잡고 있으면 뒤의 dead letter 까지 막힌다.
    @Test
    void SubmitRun_으로_읽히지_않는_본문은_던지지_않고_버린다() {
        Message broken = new Message("not json".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        assertThatCode(() -> sut.onMessage(broken)).doesNotThrowAnyException();

        then(executionService).shouldHaveNoInteractions();
        then(nodeTestRunService).shouldHaveNoInteractions();
    }

    private static Message deadLetter(String mode, String reason) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setType("SubmitRun");
        properties.setMessageId(EXECUTION_ID);

        if (reason != null) {
            properties.setHeader("x-first-death-reason", reason);
        }

        String body = """
                {"execution_id":"%s","mode":"%s","workflow":{"id":"w1","nodes":[]},"input":null,"comment":null,
                 "mock":false,"files":[],"ref_files":{},"submitted_at":"2026-10-16T05:00:00Z"}"""
                .formatted(EXECUTION_ID, mode);

        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
