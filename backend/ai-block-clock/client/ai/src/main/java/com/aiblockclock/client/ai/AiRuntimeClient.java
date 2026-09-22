package com.aiblockclock.client.ai;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RequiredArgsConstructor
public class AiRuntimeClient {
    private static final String RUNS_PATH = "/v1/runs";
    private static final String HEALTH_PATH = "/v1/health";

    private final RestClient runtimeRestClient;

    public RunAccepted start(RunRequest request) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("workflow", request.workflow());
        form.add("mock", String.valueOf(request.mock()));
        addIfPresent(form, "input", request.input());
        addIfPresent(form, "comment", request.comment());

        try {
            return require(runtimeRestClient.post()
                    .uri(RUNS_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(RunAccepted.class), "run acceptance");
        } catch (RestClientException exception) {
            throw new AiClientException(AiClientError.REQUEST_FAILED, "run request failed", exception);
        }
    }

    public RunStatus read(String runId) {
        return require(get(RUNS_PATH + "/" + runId, RunStatus.class, "run status"), "run status");
    }

    public String readArtifact(String runId) {
        return require(get(RUNS_PATH + "/" + runId + "/artifact", String.class, "artifact"), "artifact");
    }

    public RuntimeHealth health() {
        return require(get(HEALTH_PATH, RuntimeHealth.class, "health"), "health");
    }

    private <T> T get(String path, Class<T> responseType, String what) {
        try {
            return runtimeRestClient.get().uri(path).retrieve().body(responseType);
        } catch (RestClientException exception) {
            throw new AiClientException(AiClientError.REQUEST_FAILED,
                    what + " request to " + path + " failed", exception);
        }
    }

    private static void addIfPresent(MultiValueMap<String, Object> form, String name, String value) {
        if (value != null && !value.isBlank()) {
            form.add(name, value);
        }
    }

    private static <T> T require(T response, String what) {
        if (response == null) {
            throw new AiClientException(AiClientError.MALFORMED_RESPONSE, "runtime response has no " + what);
        }

        return response;
    }
}
