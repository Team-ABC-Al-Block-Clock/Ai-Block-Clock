package com.aiblockclock.client.ai;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiRuntimeClientTest {
    private static final String BASE_URL = "http://ai-block-clock-ai-runtime:8000";
    private static final Pattern FORM_PART =
            Pattern.compile("Content-Disposition: form-data; name=\"([^\"]+)\"\\r\\n(?:[^\\r\\n]+\\r\\n)*\\r\\n(.*?)\\r\\n--", Pattern.DOTALL);

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
                .andExpect(formParts(Map.of(
                        "workflow", "{\"nodes\":[]}", "mock", "true", "input", "{\"text\":\"성적표\"}")))
                .andRespond(withStatus(HttpStatus.ACCEPTED).body("""
                        { "run_id": "20260919-100451-688", "status": "queued",
                          "node_count": 2, "poll_url": "/v1/runs/20260919-100451-688",
                          "poll_interval_ms": 2000 }
                        """).contentType(MediaType.APPLICATION_JSON));

        RunAccepted accepted = client.start(new RunRequest("{\"nodes\":[]}", "{\"text\":\"성적표\"}", null, null, Map.of(), true));

        assertThat(accepted.runId()).isEqualTo("20260919-100451-688");
        assertThat(accepted.nodeCount()).isEqualTo(2);
        assertThat(accepted.pollIntervalMs()).isEqualTo(2000);
        server.verify();
    }

    @Test
    @DisplayName("파일은 확장자가 남은 이름으로 실어 보낸다")
    void sendsTheStoredFileAsAFilePart(@TempDir Path directory) throws Exception {
        Path stored = Files.write(directory.resolve("input.xlsx"), "점수".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(BASE_URL + "/v1/runs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(filePart("input.xlsx", "점수"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).body("""
                        { "run_id": "run-1", "status": "queued", "node_count": 1,
                          "poll_url": "/v1/runs/run-1", "poll_interval_ms": 2000 }
                        """).contentType(MediaType.APPLICATION_JSON));

        client.start(RunRequest.of("{\"nodes\":[]}", null, stored, true));

        server.verify();
    }

    @Test
    @DisplayName("참고 자료는 단계 번호를 붙인 칸으로 나간다")
    void sendsRefFilesUnderTheirNodeId(@TempDir Path directory) throws Exception {
        Path first = Files.write(directory.resolve("0.pdf"), "기준표".getBytes(StandardCharsets.UTF_8));
        Path second = Files.write(directory.resolve("1.md"), "양식".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(BASE_URL + "/v1/runs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(partNames("ref_file_n1_0", "ref_file_n1_1", "ref_file_n2_0"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).body("""
                        { "run_id": "run-1", "status": "queued", "node_count": 2,
                          "poll_url": "/v1/runs/run-1", "poll_interval_ms": 2000 }
                        """).contentType(MediaType.APPLICATION_JSON));

        client.start(new RunRequest("{\"nodes\":[]}", null, null, null,
                Map.of("n1", List.of(first, second), "n2", List.of(first)), true));

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
    @DisplayName("멎은 까닭은 error_code 와 fault 로 온다 — code · message 라는 칸은 계약에 없다")
    void readsFailureByContractFieldNames() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1"))
                .andRespond(withSuccess("""
                        { "run_id": "run-1", "status": "failed",
                          "progress": { "done": 1, "total": 2, "percent": 50 },
                          "nodes": [ { "id": "n1", "name": "성적 분석", "type": "llm", "status": "succeeded" },
                                     { "id": "n2", "name": "가정통신문 작성", "type": "llm", "status": "failed" } ],
                          "failure": { "node_id": "n2", "error_code": "MODEL_API_ERROR",
                                       "fault": "supplier", "affects_success_rate": true,
                                       "refund_scope": { "refund_node_ids": ["n2"],
                                                         "settled_node_ids": ["n1"] } } }
                        """, MediaType.APPLICATION_JSON));

        RunStatus status = client.read("run-1");

        assertThat(status.isFinished()).isTrue();
        assertThat(status.isSucceeded()).isFalse();
        assertThat(status.failure()).isEqualTo(
                new RunStatus.Failure("n2", "MODEL_API_ERROR", "supplier"));
        server.verify();
    }

    @Test
    void 실패하면_단계별_성공_여부와_실패_책임을_읽는다() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1"))
                .andRespond(withSuccess("""
                        { "run_id": "run-1", "status": "failed",
                          "nodes": [
                            { "id": "n1", "type": "llm", "status": "succeeded", "cost_abc": 0.0021 },
                            { "id": "n2", "type": "llm", "status": "failed", "cost_abc": 0.0004 } ],
                          "not_executed": [],
                          "failure": { "node_id": "n2", "error_code": "RATE_LIMITED", "fault": "platform",
                                       "affects_success_rate": false,
                                       "refund_scope": { "refund_node_ids": ["n1", "n2"], "settled_node_ids": ["n1"] } } }
                        """, MediaType.APPLICATION_JSON));

        RunStatus status = client.read("run-1");

        assertThat(status.nodes()).extracting(RunStatus.Node::isSucceeded).containsExactly(true, false);
        assertThat(status.nodes().get(0).costAbc()).isEqualByComparingTo("0.0021");
        assertThat(status.failure().isPlatformFault()).isTrue();
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

    @Test
    @DisplayName("실행 번호는 인코딩해서 경로에 넣는다 — 다른 경로로 새지 않는다")
    void encodesRunIdIntoPath() {
        server.expect(requestTo(BASE_URL + "/v1/runs/a%2F..%2Fhealth%3Fx%3D1"))
                .andRespond(withSuccess("""
                        { "run_id": "a/../health?x=1", "status": "running" }
                        """, MediaType.APPLICATION_JSON));

        client.read("a/../health?x=1");

        server.verify();
    }

    @Test
    @DisplayName("모르는 실행 상태가 오면 끝난 실패로 본다 — 폴링이 끝나지 않는 것보다 낫다")
    void treatsUnknownStateAsFinishedFailure() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1"))
                .andRespond(withSuccess("""
                        { "run_id": "run-1", "status": "partial" }
                        """, MediaType.APPLICATION_JSON));

        RunStatus status = client.read("run-1");

        assertThat(status.state()).isEqualTo(RunState.UNKNOWN);
        assertThat(status.isFinished()).isTrue();
        assertThat(status.isSucceeded()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("실행 요청이 실패하면 요청 실패로 끊는다")
    void failsWhenRunRequestFails() {
        server.expect(requestTo(BASE_URL + "/v1/runs"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertError(() -> client.start(request()), AiClientError.REQUEST_FAILED);
        server.verify();
    }

    @Test
    @DisplayName("접수 응답 본문이 비어 오면 형식 오류다")
    void failsWhenAcceptanceIsEmpty() {
        server.expect(requestTo(BASE_URL + "/v1/runs"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertError(() -> client.start(request()), AiClientError.MALFORMED_RESPONSE);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{ \"status\": \"queued\", \"poll_interval_ms\": 2000 }",
            "{ \"run_id\": \" \", \"poll_interval_ms\": 2000 }",
            "{ \"run_id\": \"run-1\" }",
            "{ \"run_id\": \"run-1\", \"poll_interval_ms\": 0 }"})
    @DisplayName("실행 번호나 폴링 간격이 빠진 접수 응답은 형식 오류다")
    void failsWhenAcceptanceLacksRunIdOrInterval(String body) {
        server.expect(requestTo(BASE_URL + "/v1/runs"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).body(body).contentType(MediaType.APPLICATION_JSON));

        assertError(() -> client.start(request()), AiClientError.MALFORMED_RESPONSE);
        server.verify();
    }

    @Test
    @DisplayName("결과물은 봉투 JSON 을 문자열 그대로 받는다")
    void readsArtifactAsIs() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1/artifact"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        { "schema_id": "common.document@1", "body": {} }""", MediaType.APPLICATION_JSON));

        assertThat(client.readArtifact("run-1")).contains("common.document@1");
        server.verify();
    }

    @Test
    @DisplayName("결과물이 아직이면 요청 실패로 끊는다")
    void failsWhenArtifactIsNotReady() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1/artifact"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertError(() -> client.readArtifact("run-1"), AiClientError.REQUEST_FAILED);
        server.verify();
    }

    @Test
    @DisplayName("건강 상태를 읽는다")
    void readsHealth() {
        server.expect(requestTo(BASE_URL + "/v1/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        { "status": "ok", "auth_required": true, "runs_in_memory": 0 }
                        """, MediaType.APPLICATION_JSON));

        RuntimeHealth health = client.health();

        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.authRequired()).isTrue();
        server.verify();
    }

    private static RunRequest request() {
        return RunRequest.of("{\"nodes\":[]}", null, null, true);
    }

    // 폼의 칸 이름과 값을 본문에서 직접 읽는다. 이름에 오타가 나면 여기서 걸린다.
    private static RequestMatcher partNames(String... expected) {
        return request -> {
            String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);

            for (String name : expected) {
                assertThat(body).contains("name=\"" + name + "\"");
            }
        };
    }

    private static RequestMatcher filePart(String expectedName, String expectedContent) {
        return request -> {
            String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);

            assertThat(body)
                    .contains("name=\"file\"")
                    .contains("filename=\"" + expectedName + "\"")
                    .contains(expectedContent);
        };
    }

    private static RequestMatcher formParts(Map<String, String> expected) {
        return request -> {
            String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
            Map<String, String> parts = new HashMap<>();
            Matcher matcher = FORM_PART.matcher(body);
            while (matcher.find()) {
                parts.put(matcher.group(1), matcher.group(2));
            }

            assertThat(parts).isEqualTo(expected);
        };
    }

    private static void assertError(ThrowingCallable call, AiClientError expected) {
        assertThatThrownBy(call)
                .isInstanceOf(AiClientException.class)
                .extracting(e -> ((AiClientException) e).getError())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("결과 PDF 를 바이트로 받는다")
    void readsPdf() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1/pdf"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("%PDF-1.7".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_PDF));

        assertThat(client.readPdf("run-1")).hasValueSatisfying(pdf ->
                assertThat(new String(pdf, StandardCharsets.UTF_8)).isEqualTo("%PDF-1.7"));
        server.verify();
    }

    @Test
    @DisplayName("PDF 가 아직 렌더되지 않았으면 실패가 아니라 없는 것으로 본다")
    void treatsUnrenderedPdfAsAbsent() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1/pdf"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThat(client.readPdf("run-1")).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("PDF 요청이 409 말고 다른 이유로 실패하면 끊는다")
    void failsWhenPdfRequestBreaks() {
        server.expect(requestTo(BASE_URL + "/v1/runs/run-1/pdf"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertError(() -> client.readPdf("run-1"), AiClientError.REQUEST_FAILED);
        server.verify();
    }
}
