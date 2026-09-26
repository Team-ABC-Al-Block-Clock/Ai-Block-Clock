package com.aiblockclock.client.ai;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public class AiRuntimeClient {
    private static final String RUNS_PATH = "/v1/runs";
    private static final String RUN_PATH = "/v1/runs/{runId}";
    private static final String ARTIFACT_PATH = "/v1/runs/{runId}/artifact";
    private static final String PDF_PATH = "/v1/runs/{runId}/pdf";
    private static final String HEALTH_PATH = "/v1/health";

    private final RestClient runtimeRestClient;

    public RunAccepted start(RunRequest request) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("workflow", request.workflow());
        form.add("mock", String.valueOf(request.mock()));
        addIfPresent(form, "input", request.input());
        addIfPresent(form, "comment", request.comment());
        addIfPresent(form, request.file());
        addRefFiles(form, request.refFiles());

        try {
            return ensureUsable(require(runtimeRestClient.post()
                    .uri(RUNS_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(RunAccepted.class), "run acceptance"));
        } catch (RestClientException exception) {
            throw new AiClientException(AiClientError.REQUEST_FAILED, "run request failed", exception);
        }
    }

    // 실행 번호는 엔진이 준 값이라 경로에 그대로 잇지 않는다. 템플릿이 인코딩해 다른 경로로 새지 않게 한다.
    public RunStatus read(String runId) {
        return require(get(RUN_PATH, runId, RunStatus.class, "run status"), "run status");
    }

    public String readArtifact(String runId) {
        return require(get(ARTIFACT_PATH, runId, String.class, "artifact"), "artifact");
    }

    public Optional<byte[]> readPdf(String runId) {
        try {
            return Optional.ofNullable(runtimeRestClient.get().uri(PDF_PATH, runId).retrieve().body(byte[].class));
        } catch (RestClientException exception) {
            if (exception instanceof HttpClientErrorException http
                    && http.getStatusCode() == HttpStatus.CONFLICT) {
                return Optional.empty();
            }

            throw new AiClientException(AiClientError.REQUEST_FAILED,
                    "pdf request for run " + runId + " failed", exception);
        }
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
            throw new AiClientException(AiClientError.REQUEST_FAILED,
                    what + " request for run " + runId + " failed", exception);
        }
    }

    // 실행 번호가 없으면 다음 조회가 엉뚱한 경로를 부르고, 간격이 0 이면 폴링이 엔진을 쉬지 않고 두드린다.
    private static RunAccepted ensureUsable(RunAccepted accepted) {
        if (accepted.runId() == null || accepted.runId().isBlank()
                || accepted.pollIntervalMs() == null || accepted.pollIntervalMs() <= 0) {
            throw new AiClientException(AiClientError.MALFORMED_RESPONSE,
                    "run acceptance has no run id or poll interval");
        }

        return accepted;
    }

    private static void addIfPresent(MultiValueMap<String, Object> form, String name, String value) {
        if (value != null && !value.isBlank()) {
            form.add(name, value);
        }
    }

    private static void addIfPresent(MultiValueMap<String, Object> form, Path file) {
        if (file == null) {
            return;
        }

        form.add("file", new FileSystemResource(file));
    }

    private static void addRefFiles(MultiValueMap<String, Object> form, Map<String, List<Path>> refFiles) {
        refFiles.forEach((nodeId, files) -> {
            for (int index = 0; index < files.size(); index++) {
                form.add("ref_file_" + nodeId + "_" + index, new FileSystemResource(files.get(index)));
            }
        });
    }

    private static <T> T require(T response, String what) {
        if (response == null) {
            throw new AiClientException(AiClientError.MALFORMED_RESPONSE, "runtime response has no " + what);
        }

        return response;
    }
}
