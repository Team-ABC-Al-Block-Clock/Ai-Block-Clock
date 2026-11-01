package com.aiblockclock.client.ai;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

// 브로커에 닿지 못하면 발행은 confirm 을 기다리지 않고 실패로 알린다. 부르는 쪽(디스패처 · 노드 테스트)이 다음 차례를 정한다.
// 아무도 듣지 않는 로컬 포트로 붙는다. 컨테이너는 필요 없지만 네트워크를 타므로 통합 테스트다.
// 포트는 소켓으로 잡아 둔 채(bind 만 하고 listen 하지 않는다) 테스트가 끝날 때까지 놓지 않는다. 닫은 포트를 넘기면
// 그 사이 다른 프로세스가 그 포트를 받아 들을 수 있다. 잡아 둔 포트는 OS 가 남에게 내주지 않고, 듣는 쪽이 없어 거절된다.
@Tag("integration")
@SpringBootTest(properties = {"spring.config.import=classpath:ai.yml", "ai.messaging.confirm-timeout=1s",
        "spring.rabbitmq.host=127.0.0.1", "spring.rabbitmq.connection-timeout=1s"})
class RunQueuePublisherUnreachableTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static Socket reservedPort;

    @Autowired
    private RunQueuePublisher runQueuePublisher;

    @DynamicPropertySource
    static void 아무도_듣지_않는_포트로_붙는다(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.port", RunQueuePublisherUnreachableTest::reservePort);
    }

    @AfterAll
    static void 잡아_둔_포트를_놓는다() throws IOException {
        if (reservedPort != null) {
            reservedPort.close();
        }
    }

    @Test
    void 브로커에_닿지_못하면_발행은_REQUEST_FAILED_다() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                assertThatThrownBy(() -> runQueuePublisher.submit(submit()))
                        .isInstanceOfSatisfying(AiClientException.class, exception ->
                                assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED)));
    }

    @Test
    void 브로커에_닿지_못하면_취소도_REQUEST_FAILED_다() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                assertThatThrownBy(() -> runQueuePublisher.cancel(UUID.randomUUID().toString()))
                        .isInstanceOfSatisfying(AiClientException.class, exception ->
                                assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED)));
    }

    private static synchronized int reservePort() {
        if (reservedPort == null) {
            try {
                reservedPort = new Socket();
                reservedPort.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        return reservedPort.getLocalPort();
    }

    private static SubmitRun submit() {
        return new SubmitRun(UUID.randomUUID().toString(), RunMode.RUN, MAPPER.readTree("{\"id\":\"w1\",\"nodes\":[]}"),
                null, null, true, List.of(), Map.of(), Instant.parse("2026-10-16T05:00:00Z"));
    }
}
