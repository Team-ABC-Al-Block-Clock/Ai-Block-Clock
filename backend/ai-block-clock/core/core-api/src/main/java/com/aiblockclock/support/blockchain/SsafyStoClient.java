package com.aiblockclock.support.blockchain;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SsafyStoClient {
    private final RestClient restClient;

    public SsafyStoClient(@Qualifier("ssafyStoRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public boolean isHealthy() {
        Boolean healthy = restClient.get()
                .uri("/health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Boolean.class);

        if (healthy == null) {
            throw new RestClientException("SSAFY STO health response is empty");
        }
        return healthy;
    }
}
