package com.aiblockclock.client.ai;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.retry.RetryPolicySettings;
import org.springframework.core.retry.RetryPolicy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

// 설정이 만드는 빈을 컨텍스트 없이 본다. 리스너 재시도 정책(B22 · B23), 결과 볼륨(B14), 토폴로지(B48), 발행자(B46),
// 엔진 · 추천 HTTP 클라이언트(B29 · B39 의 조회).
class AiClientConfigurationTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(2);
    private static final String SERVICE_TOKEN = "shared-secret";

    private final AiClientConfiguration sut = new AiClientConfiguration();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedToken = new AtomicReference<>();
    private HttpServer server;

    @TempDir
    private Path resultsRoot;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // 거절한 메시지는 다시 시도해도 읽히지 않는다(B22). 그 밖의 처리 실패만 다시 시도한다(B23).
    @Test
    void 리스너는_거절한_메시지는_다시_시도하지_않고_처리_실패는_다시_시도한다() {
        RetryPolicy policy = listenerRetryPolicy();

        assertThat(policy.shouldRetry(new AmqpRejectAndDontRequeueException("읽을 수 없는 실행 이벤트"))).isFalse();
        assertThat(policy.shouldRetry(new IllegalStateException("db down"))).isTrue();
    }

    // 리스너 컨테이너는 리스너가 던진 것을 ListenerExecutionFailedException 으로 감싸 재시도 정책에 넘긴다.
    @Test
    void 컨테이너가_감싼_거절도_다시_시도하지_않는다() {
        RetryPolicy policy = listenerRetryPolicy();
        Message message = new Message(new byte[0], new MessageProperties());

        assertThat(policy.shouldRetry(new ListenerExecutionFailedException("listener failed",
                new AmqpRejectAndDontRequeueException("읽을 수 없는 실행 이벤트"), message))).isFalse();
        assertThat(policy.shouldRetry(new ListenerExecutionFailedException("listener failed",
                new IllegalStateException("db down"), message))).isTrue();
    }

    @Test
    void 결과_볼륨은_설정한_루트_아래_실행_번호_폴더의_artifact_json_을_읽는다() throws IOException {
        Files.createDirectories(resultsRoot.resolve("run-1"));
        Files.writeString(resultsRoot.resolve("run-1").resolve("artifact.json"), "{\"a\":1}");

        RunResultVolume volume = sut.runResultVolume(new RuntimeProperties(URI.create("http://localhost:8000"),
                Duration.ofSeconds(1), Duration.ofSeconds(1), resultsRoot));

        assertThat(volume.readArtifact("run-1"))
                .hasValueSatisfying(bytes -> assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}"));
    }

    // confirm 이 오지 않는 브로커다(RabbitTemplate 목은 confirm 을 채우지 않는다). 설정한 300ms 는 기다리고,
    // 설정을 버리고 기본값(5초)으로 기다리지는 않는다.
    @Test
    void 발행자_빈은_주어진_RabbitTemplate_으로_보내고_confirm_은_설정한_기한까지만_기다린다() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        Duration confirmTimeout = Duration.ofMillis(300);
        RunQueuePublisher publisher = sut.runQueuePublisher(rabbitTemplate, new MessagingProperties(confirmTimeout));
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> publisher.cancel("x"))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(AiClientError.REQUEST_FAILED));

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                .isGreaterThanOrEqualTo(confirmTimeout)
                .isLessThan(Duration.ofSeconds(4));
        then(rabbitTemplate).should().send(eq("abc.control"), eq(""), any(Message.class), any(CorrelationData.class));
    }

    // 계약 그대로인지는 RunTopologyTest 가 본다. 여기서는 빈이 그 선언을 빠짐없이 내놓는지만 본다.
    @Test
    void 토폴로지_빈은_RunTopology_가_정한_선언을_그대로_내놓는다() {
        assertThat(sut.runTopology().getDeclarables())
                .extracting(Object::toString)
                .containsExactlyElementsOf(RunTopology.declarables().getDeclarables().stream()
                        .map(Object::toString)
                        .toList());
    }

    // 감시 · 노드 테스트가 기한을 넘긴 실행을 엔진에 물을 때 쓰는 클라이언트다.
    @Test
    void 엔진_클라이언트는_설정한_주소로_서비스_토큰을_실어_묻는다() throws IOException {
        URI baseUrl = serve("{\"run_id\":\"x\",\"status\":\"running\"}");
        AiRuntimeClient client = sut.aiRuntimeClient(
                new RuntimeProperties(baseUrl, HTTP_TIMEOUT, HTTP_TIMEOUT, resultsRoot), new AiProperties(SERVICE_TOKEN));

        RunStatus status = client.read("x");

        assertThat(status.runId()).isEqualTo("x");
        assertThat(status.state()).isEqualTo(RunState.RUNNING);
        assertThat(receivedPath.get()).isEqualTo("/v1/runs/x");
        assertThat(receivedToken.get()).isEqualTo(SERVICE_TOKEN);
    }

    @Test
    void 추천_클라이언트는_설정한_주소로_서비스_토큰을_실어_묻는다() throws IOException {
        URI baseUrl = serve("""
                {"recommendations":[],"model_version":"abc-agent-retriever-v1","latency_ms":3}""");
        RecommendClient client = sut.recommendClient(
                new RecommendProperties(baseUrl, HTTP_TIMEOUT, HTTP_TIMEOUT), new AiProperties(SERVICE_TOKEN));

        assertThat(client.recommend("성적표로 리포트를 쓰고 싶어", 3)).isEmpty();

        assertThat(receivedPath.get()).isEqualTo("/v1/recommend");
        assertThat(receivedToken.get()).isEqualTo(SERVICE_TOKEN);
    }

    // 받은 경로와 서비스 토큰을 남기고 body 를 JSON 으로 돌려주는 로컬 서버다. 포트는 비어 있는 것을 받는다.
    private URI serve(String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedToken.set(exchange.getRequestHeaders().getFirst(AiProperties.SERVICE_TOKEN_HEADER));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private RetryPolicy listenerRetryPolicy() {
        RetryPolicySettings settings = new RetryPolicySettings();

        sut.listenerRetrySettingsCustomizer().customize(settings);

        return settings.createRetryPolicy();
    }
}
