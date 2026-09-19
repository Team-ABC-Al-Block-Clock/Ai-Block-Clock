package com.aiblockclock.support.blockchain;

import java.math.BigInteger;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SsafyStoClient {
    private final RestClient queryRestClient;
    private final RestClient transactionRestClient;

    public SsafyStoClient(
            @Qualifier("ssafyStoQueryRestClient") RestClient queryRestClient,
            @Qualifier("ssafyStoTransactionRestClient") RestClient transactionRestClient
    ) {
        this.queryRestClient = queryRestClient;
        this.transactionRestClient = transactionRestClient;
    }

    public boolean isHealthy() {
        return getBoolean("/health", "health");
    }

    public SsafyTokenDeploymentReceipt deployToken(SsafyTokenDeploymentRequest request) {
        SsafyTokenDeploymentReceipt receipt = transactionRestClient.post()
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

    public List<SsafyDeployedToken> getDeployedTokens(String ownerAddress) {
        SsafyBlockchainAddress.requireValid(ownerAddress, "ownerAddress");
        List<SsafyDeployedToken> tokens = queryRestClient.get()
                .uri("/v1/tokens/{ownerAddress}", ownerAddress)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (tokens == null) {
            throw new RestClientException("SSAFY STO deployed-token response is empty");
        }
        return List.copyOf(tokens);
    }

    public String getTokenName(String contractAddress) {
        return getRequiredText(tokenPath(contractAddress, "/name"), "token name");
    }

    public String getTokenSymbol(String contractAddress) {
        return getRequiredText(tokenPath(contractAddress, "/symbol"), "token symbol");
    }

    public BigInteger getGranularity(String contractAddress) {
        return getInteger(tokenPath(contractAddress, "/granularity"), "token granularity", false);
    }

    public BigInteger getTotalSupply(String contractAddress) {
        return getInteger(tokenPath(contractAddress, "/total-supply"), "token total supply", true);
    }

    public BigInteger getBalance(String contractAddress, String holderAddress) {
        SsafyBlockchainAddress.requireValid(contractAddress, "contractAddress");
        SsafyBlockchainAddress.requireValid(holderAddress, "holderAddress");

        String response = queryRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/tokens/{contractAddress}/balance")
                        .queryParam("holderAddress", holderAddress)
                        .build(contractAddress))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        return parseInteger(response, "token balance", true);
    }

    public boolean isMinter(String contractAddress, String address) {
        SsafyBlockchainAddress.requireValid(contractAddress, "contractAddress");
        SsafyBlockchainAddress.requireValid(address, "address");

        String response = queryRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/tokens/{contractAddress}/roles/minter")
                        .queryParam("address", address)
                        .build(contractAddress))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        return parseBoolean(response, "minter role");
    }

    public boolean isIssuable(String contractAddress) {
        return getBoolean(tokenPath(contractAddress, "/status/issuance"), "issuance status");
    }

    private String tokenPath(String contractAddress, String suffix) {
        SsafyBlockchainAddress.requireValid(contractAddress, "contractAddress");
        return "/v1/tokens/" + contractAddress + suffix;
    }

    private String getRequiredText(String path, String responseName) {
        String response = queryRestClient.get()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new RestClientException("SSAFY STO " + responseName + " response is empty");
        }
        return response.strip();
    }

    private BigInteger getInteger(String path, String responseName, boolean allowZero) {
        String response = queryRestClient.get()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        return parseInteger(response, responseName, allowZero);
    }

    private boolean getBoolean(String path, String responseName) {
        String response = queryRestClient.get()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        return parseBoolean(response, responseName);
    }

    private BigInteger parseInteger(String response, String responseName, boolean allowZero) {
        if (response == null || response.isBlank()) {
            throw new RestClientException("SSAFY STO " + responseName + " response is empty");
        }

        try {
            BigInteger value = new BigInteger(response.strip());
            if (allowZero ? value.signum() < 0 : value.signum() <= 0) {
                throw new RestClientException("SSAFY STO " + responseName + " response is out of range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new RestClientException("SSAFY STO " + responseName + " response must be an integer", exception);
        }
    }

    private boolean parseBoolean(String response, String responseName) {
        if (response == null || response.isBlank()) {
            throw new RestClientException("SSAFY STO " + responseName + " response is empty");
        }
        return switch (response.strip()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new RestClientException(
                    "SSAFY STO " + responseName + " response must be true or false"
            );
        };
    }
}
