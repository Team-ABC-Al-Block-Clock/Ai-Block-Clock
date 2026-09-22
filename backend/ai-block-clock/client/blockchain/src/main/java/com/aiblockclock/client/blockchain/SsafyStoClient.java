package com.aiblockclock.client.blockchain;

import java.math.BigInteger;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class SsafyStoClient {
    private final RestClient queryRestClient;

    public boolean isHealthy() {
        return getBoolean("/health", "health");
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
