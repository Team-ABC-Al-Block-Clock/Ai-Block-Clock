package com.aiblockclock.client.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiRuntimeClientTest {
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
    @DisplayName("실행 요청은 JSON 이 아니라 폼으로 보낸다 — 파일이 함께 가기 때문이다")
    void sendsRunRequestAsMultipartForm() {
        server.expect(requestTo(BASE_URL + "/v1/runs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withStatus(HttpStatus.ACCEPTED).body("""
                        { "run_id": "20260919-100451-688", "status": "queued",
                          "node_count": 2, "poll_url": "/v1/runs/20260919-100451-688",
                          "poll_interval_ms": 2000 }
                        """).contentType(MediaType.APPLICATION_JSON));

        RunAccepted accepted = client.start(new RunRequest("{\"nodes\":[]}", "{\"text\":\"성적표\"}", null, true));

        assertThat(accepted.runId()).isEqualTo("20260919-100451-688");
        assertThat(accepted.nodeCount()).isEqualTo(2);
        assertThat(accepted.pollIntervalMs()).isEqualTo(2000);
        server.verify();
    }

    @Test
    @DisplayName("진행 상황은 우리가 읽지 않는 칸이 섞여 와도 깨지지 않는다")
    void readsProgressBesideFieldsWeDoNotUse() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        { "run_id": "run-1", "status": "running", "mock": true,
                          "workflow": { "id": "wf", "name": "성적 분석", "node_count": 2 },
                          "progress": { "done": 1, "total": 2, "percent": 50,
                                        "current_index": 2, "current_node_id": "n2",
                                        "current_node_name": "가정통신문 작성" },
                          "nodes": [
                            { "id": "n1", "name": "성적 분석", "type": "llm", "status": "succeeded",
                              "owner_id": "u_2001", "agent_id": null, "cost_abc": 3.76,
                              "input_tokens": 1281, "repair_count": 1, "skip_reason": null },
                            { "id": "n2", "name": "가정통신문 작성", "type": "llm", "status": "running",
                              "cost_abc": null, "skip_reason": null } ],
                          "totals": { "cost_abc": 3.76 },
                          "contribution": [ { "node_id": "n1", "owner_id": "u_2001",
                                              "agent_id": null, "charged_abc": 4.2 } ],
                          "not_executed": [], "failure": null, "result": null }
                        """, MediaType.APPLICATION_JSON));

        RunStatus status = client.read("run-1");

        assertThat(status.isFinished()).isFalse();
        assertThat(status.progress().percent()).isEqualTo(50);
        assertThat(status.progress().currentNodeName()).isEqualTo("가정통신문 작성");
        assertThat(status.nodes()).extracting(RunStatus.Node::status)
                .containsExactly("succeeded", "running");
        assertThat(status.contribution()).singleElement()
                .extracting(RunStatus.Contribution::ownerId)
                .isEqualTo("u_2001");
        server.verify();
    }

    @Test
    @DisplayName("끝나면 결과 주소가 함께 온다")
    void readsResultWhenFinished() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1"))
                .andRespond(withSuccess("""
                        { "run_id": "run-1", "status": "succeeded",
                          "progress": { "done": 2, "total": 2, "percent": 100 },
                          "nodes": [], "contribution": [], "not_executed": [],
                          "result": { "type": "pdf", "schema_id": "common.document@1",
                                      "artifact_url": "/v1/runs/run-1/artifact",
                                      "pdf_url": "/v1/runs/run-1/pdf" } }
                        """, MediaType.APPLICATION_JSON));

        RunStatus status = client.read("run-1");

        assertThat(status.isSucceeded()).isTrue();
        assertThat(status.result().artifactUrl()).isEqualTo("/v1/runs/run-1/artifact");
        server.verify();
    }

    @Test
    @DisplayName("실행 엔진이 죽어 있으면 요청 실패로 끊는다")
    void failsWhenTheRuntimeIsDown() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.read("run-1"))
                .isInstanceOf(AiClientException.class)
                .extracting(e -> ((AiClientException) e).getError())
                .isEqualTo(AiClientError.REQUEST_FAILED);
        server.verify();
    }
}
