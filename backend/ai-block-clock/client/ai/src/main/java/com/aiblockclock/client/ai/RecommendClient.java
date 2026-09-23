package com.aiblockclock.client.ai;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class RecommendClient {
    public static final String SERVICE_TOKEN_HEADER = "X-ABC-Service-Token";

    private static final String INDEX_PATH = "/v1/index/agents";
    private static final String RECOMMEND_PATH = "/v1/recommend";

    private final RestClient recommendRestClient;

    public IndexOutcome upsert(List<IndexedAgent> agents) {
        return index(IndexOperation.UPSERT, agents);
    }

    public IndexOutcome rebuild(List<IndexedAgent> agents) {
        return index(IndexOperation.REBUILD, agents);
    }

    public List<RecommendedAgent> recommend(String query, int topK) {
        Map<String, Object> body = Map.of("query", query, "top_k", topK);

        return require(post(RECOMMEND_PATH, body, Recommendations.class), "recommendations")
                .recommendations();
    }

    private IndexOutcome index(IndexOperation op, List<IndexedAgent> agents) {
        Map<String, Object> body = Map.of("op", op.wireName(), "agents", agents);

        return require(post(INDEX_PATH, body, IndexOutcome.class), "index outcome");
    }

    private <T> T post(String path, Map<String, Object> body, Class<T> responseType) {
        try {
            return recommendRestClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException exception) {
            throw new RecommendClientException(RecommendClientError.REQUEST_FAILED,
                    "recommend request to " + path + " failed", exception);
        }
    }

    private static <T> T require(T response, String what) {
        if (response == null) {
            throw new RecommendClientException(RecommendClientError.MALFORMED_RESPONSE,
                    "recommend response has no " + what);
        }

        return response;
    }
}
