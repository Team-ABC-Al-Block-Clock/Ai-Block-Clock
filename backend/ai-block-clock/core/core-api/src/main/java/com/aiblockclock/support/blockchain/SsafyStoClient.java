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
        String response = restClient.get()
                .uri("/health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new RestClientException("SSAFY STO health response is empty");
        }
        return switch (response.strip()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new RestClientException("SSAFY STO health response must be true or false");
        };
    }

    public SsafyTokenDeploymentReceipt deployToken(SsafyTokenDeploymentRequest request) {
        SsafyTokenDeploymentReceipt receipt = restClient.post()
                .uri("/v1/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SsafyTokenDeploymentReceipt.class);

        if (receipt == null || receipt.status() == null
                || receipt.transactionHash() == null || receipt.transactionHash().isBlank()
                || Boolean.TRUE.equals(receipt.status())
                && (receipt.contractAddress() == null || receipt.contractAddress().isBlank())) {
            throw new RestClientException("SSAFY STO deployment receipt is incomplete; verify chain outcome before resubmitting");
        }
        return receipt;
    }
}
