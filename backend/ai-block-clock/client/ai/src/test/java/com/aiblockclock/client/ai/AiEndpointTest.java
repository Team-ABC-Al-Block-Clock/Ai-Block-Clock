package com.aiblockclock.client.ai;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AiEndpointTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final AtomicReference<String> receivedToken = new AtomicReference<>();
    private HttpServer server;
    private URI baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst(AiProperties.SERVICE_TOKEN_HEADER));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("서비스 토큰이 있으면 모든 요청에 헤더로 싣는다")
    void sendsServiceTokenHeader() {
        call(new AiProperties("shared-secret"));

        assertThat(receivedToken.get()).isEqualTo("shared-secret");
    }

    @Test
    @DisplayName("서비스 토큰이 비어 있으면 헤더를 싣지 않는다")
    void omitsHeaderWithoutToken() {
        call(new AiProperties(" "));

        assertThat(receivedToken.get()).isNull();
    }

    private void call(AiProperties shared) {
        AiEndpoint.restClient(baseUrl, TIMEOUT, TIMEOUT, shared)
                .get().uri("/v1/health").retrieve().body(String.class);
    }
}
