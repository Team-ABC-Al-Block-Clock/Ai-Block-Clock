package com.aiblockclock.client.ai;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// 실행 엔진의 HTTP 다. 실행을 맡기는 일은 브로커(RunQueuePublisher)가 하고, 여기에는 대조(안전망)와 근거 백필에 쓰는
// 조회만 남았다 (ai/docs/integration-guide.md §4.9). run_id 는 곧 실행 번호다.
@RequiredArgsConstructor
public class AiRuntimeClient {
    private static final String RUN_PATH = "/v1/runs/{runId}";
    private static final String ARTIFACT_PATH = "/v1/runs/{runId}/artifact";
    private static final String HEALTH_PATH = "/v1/health";
    private static final String RECORD_PATH = "/v1/runs/{runId}?include_node_output=true";
    private static final String RUN_NOT_FOUND = "RUN_NOT_FOUND";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RestClient runtimeRestClient;

    // 실행 번호는 경로에 그대로 잇지 않는다. 템플릿이 인코딩해 다른 경로로 새지 않게 한다.
    public RunStatus read(String runId) {
        return require(get(RUN_PATH, runId, RunStatus.class, "run status"), "run status");
    }

    // 정산 근거로 남길 원본이다. 노드 출력 전문까지 받는다. 받은 바이트를 그대로 두고, 읽은 것은 함께 돌려준다.
    public RunRecord readRecord(String runId) {
        String json = require(get(RECORD_PATH, runId, String.class, "run record"), "run record");

        try {
            return new RunRecord(require(MAPPER.readValue(json, RunStatus.class), "run record"), json);
        } catch (JacksonException exception) {
            throw new AiClientException(AiClientError.MALFORMED_RESPONSE,
                    "run record for run " + runId + " is not a run status", exception);
        }
    }

    public String readArtifact(String runId) {
        return require(get(ARTIFACT_PATH, runId, String.class, "artifact"), "artifact");
    }

    public RuntimeHealth health() {
        try {
            return require(runtimeRestClient.get().uri(HEALTH_PATH).retrieve().body(RuntimeHealth.class), "health");
        } catch (RestClientException exception) {
            throw new AiClientException(AiClientError.REQUEST_FAILED, "health request failed", exception);
        }
    }

    private <T> T get(String path, String runId, Class<T> responseType, String what) {
        try {
            return runtimeRestClient.get().uri(path, runId).retrieve().body(responseType);
        } catch (RestClientException exception) {
            throw new AiClientException(lookupErrorOf(exception),
                    what + " request for run " + runId + " failed", exception);
        }
    }

    // 404 라도 본문이 RUN_NOT_FOUND 일 때만 "엔진이 모른다" 로 본다. 주소를 잘못 잡아 다른 서버가 준 404 는 닿지 못한 것이다.
    private static AiClientError lookupErrorOf(RestClientException exception) {
        if (exception instanceof RestClientResponseException response
                && response.getStatusCode() == HttpStatus.NOT_FOUND
                && response.getResponseBodyAsString().contains(RUN_NOT_FOUND)) {
            return AiClientError.RUN_NOT_FOUND;
        }

        return AiClientError.REQUEST_FAILED;
    }

    private static <T> T require(T response, String what) {
        if (response == null) {
            throw new AiClientException(AiClientError.MALFORMED_RESPONSE, "runtime response has no " + what);
        }

        return response;
    }
}
