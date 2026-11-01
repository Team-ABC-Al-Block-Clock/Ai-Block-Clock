package com.aiblockclock.client.ai;

import com.github.dockerjava.api.DockerClient;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.DockerClientFactory;
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

// B46 보강 (E2E 버그 2) — 브로커가 연결은 받지만 답하지 않는다(일시 정지 · 자원 경보 · 네트워크 단절).
// 발행 · 취소는 그래도 confirm 기한(1.5초) 남짓 안에 REQUEST_FAILED 로 끝나야 한다. 처음 한두 번은 쉬던 채널로 보내
// confirm 기한에서 끊기지만, confirm 을 못 받은 채널은 캐시로 돌아오지 않아 곧 새 채널을 열어야 하는 차례가 온다.
// 그 채널 열기가 기한 없이 막히면 안 된다. 그래서 한 번이 아니라 여러 번 이어 보내 본다.
// 브로커를 멈추면 다른 테스트를 막으므로 이 클래스만 제 브로커를 띄운다(설정 값이 이웃과 달라 컨텍스트가 따로 뜬다).
@Tag("container")
@SpringBootTest(properties = {"spring.config.import=classpath:ai.yml", "ai.messaging.confirm-timeout=1500ms"})
@Import(RabbitTestConfiguration.class)
class RunQueueUnresponsiveBrokerContainerTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int ATTEMPTS = 5;
    private static final Duration BOUND = Duration.ofSeconds(10);

    @Autowired
    private RunQueuePublisher runQueuePublisher;

    @Autowired
    private RabbitMQContainer rabbit;

    @Test
    void 브로커가_답하지_않으면_발행은_매번_confirm_기한_남짓_안에_REQUEST_FAILED_다() {
        runQueuePublisher.submit(submit());

        whilePaused(() -> {
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                assertTimeoutPreemptively(BOUND, () -> assertRequestFailed(() -> runQueuePublisher.submit(submit())),
                        attempt + "번째 발행이 " + BOUND.toSeconds() + "초 안에 끝나지 않았다");
            }
        });
    }

    @Test
    void 브로커가_답하지_않으면_취소도_매번_confirm_기한_남짓_안에_REQUEST_FAILED_다() {
        runQueuePublisher.cancel(UUID.randomUUID().toString());

        whilePaused(() -> {
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                assertTimeoutPreemptively(BOUND,
                        () -> assertRequestFailed(() -> runQueuePublisher.cancel(UUID.randomUUID().toString())),
                        attempt + "번째 취소가 " + BOUND.toSeconds() + "초 안에 끝나지 않았다");
            }
        });
    }

    private static void assertRequestFailed(Runnable publish) {
        assertThatThrownBy(publish::run)
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED));
    }

    // 브로커 컨테이너를 일시 정지한다. TCP 연결은 살아 있지만 브로커는 아무것도 답하지 않는다. 무슨 일이 있어도 되살린다.
    private void whilePaused(Runnable body) {
        DockerClient docker = DockerClientFactory.instance().client();
        String container = rabbit.getContainerId();

        docker.pauseContainerCmd(container).exec();

        try {
            assertThat(docker.inspectContainerCmd(container).exec().getState().getPaused())
                    .as("브로커를 멈추지 못했다").isTrue();
            body.run();
        } finally {
            docker.unpauseContainerCmd(container).exec();
        }
    }

    private static SubmitRun submit() {
        return new SubmitRun(UUID.randomUUID().toString(), RunMode.RUN, MAPPER.readTree("{\"id\":\"w1\",\"nodes\":[]}"),
                null, null, true, List.of(), Map.of(), Instant.parse("2026-10-16T05:00:00Z"));
    }
}
