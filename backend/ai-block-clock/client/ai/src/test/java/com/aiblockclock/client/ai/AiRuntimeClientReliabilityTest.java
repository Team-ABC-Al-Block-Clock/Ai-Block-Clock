package com.aiblockclock.client.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 엔진이 실행을 잊은 것을 알아보는지, 근거 원본을 받은 그대로 돌려주는지 본다.
// 접수(POST /v1/runs) · HTTP 취소는 브로커로 옮겨 가 여기서 보지 않는다(ADR-0001).
class AiRuntimeClientReliabilityTest {
    private static final String BASE_URL = "http://ai-block-clock-ai-runtime:8000";

    private MockRestServiceServer server;
    private AiRuntimeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiRuntimeClient(builder.build());
    }

    @Test
    @DisplayName("404 RUN_NOT_FOUND 는 엔진이 실행을 잊은 것이다")
    void recognisesAForgottenRun() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("""
                        {"detail": {"code": "RUN_NOT_FOUND", "message": "run-1"}}
                        """).contentType(MediaType.APPLICATION_JSON));

        assertError(() -> client.read("run-1"), AiClientError.RUN_NOT_FOUND);
    }

    @Test
    @DisplayName("본문 없는 404 는 닿지 못한 것으로 본다 — 주소를 잘못 잡았을 때 실행을 잊었다고 닫지 않는다")
    void doesNotTakeABare404ForAForgottenRun() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertError(() -> client.read("run-1"), AiClientError.REQUEST_FAILED);
    }

    @Test
    @DisplayName("근거 원본은 노드 출력까지 받아, 받은 바이트 그대로와 읽은 것을 함께 돌려준다")
    void readsTheRecordAsIs() {
        String body = """
                {"run_id": "run-1", "status": "failed",
                 "nodes": [{"id": "n1", "name": "시장 조사", "status": "failed", "owner_id": "7", "model": "gemini-2.5-pro",
                            "input_tokens": 1200, "output_tokens": 300, "retries": 1, "repair_count": 2,
                            "cost_usd": 0.0021, "started_at": "2026-10-15T01:00:00+00:00", "duration_ms": 15000,
                            "output_payload": "{}"}],
                 "failure": {"node_id": "n1", "error_code": "SCHEMA_VALIDATION_FAILED", "fault": "supplier",
                             "affects_success_rate": true},
                 "error": {"code": "SCHEMA_VALIDATION_FAILED", "message": "형식", "detail": "competitors 가 없다", "node_id": "n1"}}
                """;
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1?include_node_output=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        RunRecord record = client.readRecord("run-1");

        assertThat(record.json()).isEqualTo(body);
        RunStatus.Node node = record.status().nodes().getFirst();
        assertThat(node.repairCount()).isEqualTo(2);
        assertThat(node.inputTokens()).isEqualTo(1200);
        assertThat(node.costUsd()).isEqualByComparingTo(new BigDecimal("0.0021"));
        assertThat(record.status().error().detail()).isEqualTo("competitors 가 없다");
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, AiClientError error) {
        assertThatThrownBy(call)
                .isInstanceOf(AiClientException.class)
                .satisfies(thrown -> assertThat(((AiClientException) thrown).getError()).isEqualTo(error));
    }
}
