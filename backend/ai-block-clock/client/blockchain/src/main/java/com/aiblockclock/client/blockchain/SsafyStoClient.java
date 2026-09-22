package com.aiblockclock.client.blockchain;

import java.math.BigInteger;
import java.util.Map;

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
        return parseBoolean(query("/health", Map.of(), "health"), "health");
    }

    public String getTokenName(String contractAddress) {
        return parseRequiredText(query(tokenPath(contractAddress, "/name"), Map.of(), "token name"), "token name");
    }

    public String getTokenSymbol(String contractAddress) {
        return parseRequiredText(query(tokenPath(contractAddress, "/symbol"), Map.of(), "token symbol"), "token symbol");
    }

    public BigInteger getGranularity(String contractAddress) {
        return parseInteger(query(tokenPath(contractAddress, "/granularity"), Map.of(), "token granularity"),
                "token granularity", false);
    }

    public BigInteger getTotalSupply(String contractAddress) {
        return parseInteger(query(tokenPath(contractAddress, "/total-supply"), Map.of(), "token total supply"),
                "token total supply", true);
    }

    public BigInteger getBalance(String contractAddress, String holderAddress) {
        SsafyBlockchainAddress.requireValid(holderAddress, "holderAddress");

        String response = query(tokenPath(contractAddress, "/balance"),
                Map.of("holderAddress", holderAddress), "token balance");
        return parseInteger(response, "token balance", true);
    }

    public boolean isMinter(String contractAddress, String address) {
        SsafyBlockchainAddress.requireValid(address, "address");

        String response = query(tokenPath(contractAddress, "/roles/minter"),
                Map.of("address", address), "minter role");
        return parseBoolean(response, "minter role");
    }

    public boolean isIssuable(String contractAddress) {
        return parseBoolean(query(tokenPath(contractAddress, "/status/issuance"), Map.of(), "issuance status"),
                "issuance status");
    }

    private String tokenPath(String contractAddress, String suffix) {
        SsafyBlockchainAddress.requireValid(contractAddress, "contractAddress");
        return "/v1/tokens/" + contractAddress + suffix;
    }

    // HTTP 오류·연결 실패·시간 초과는 체인 결과를 알 수 없는 상황이라 REQUEST_FAILED 로만 알린다.
    private String query(String path, Map<String, String> queryParams, String responseName) {
        try {
            return queryRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        queryParams.forEach(uriBuilder::queryParam);
                        return uriBuilder.build();
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException exception) {
            throw new BlockchainClientException(BlockchainClientError.REQUEST_FAILED,
                    "SSAFY STO " + responseName + " request failed", exception);
        }
    }

    private String parseRequiredText(String response, String responseName) {
        requireNonBlank(response, responseName);
        return response.strip();
    }

    private BigInteger parseInteger(String response, String responseName, boolean allowZero) {
        requireNonBlank(response, responseName);

        BigInteger value;
        try {
            value = new BigInteger(response.strip());
        } catch (NumberFormatException exception) {
            throw new BlockchainClientException(BlockchainClientError.MALFORMED_RESPONSE,
                    "SSAFY STO " + responseName + " response must be an integer", exception);
        }

        if (allowZero ? value.signum() < 0 : value.signum() <= 0) {
            throw new BlockchainClientException(BlockchainClientError.MALFORMED_RESPONSE,
                    "SSAFY STO " + responseName + " response is out of range");
        }

        return value;
    }

    private boolean parseBoolean(String response, String responseName) {
        requireNonBlank(response, responseName);

        return switch (response.strip()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new BlockchainClientException(BlockchainClientError.MALFORMED_RESPONSE,
                    "SSAFY STO " + responseName + " response must be true or false");
        };
    }

    private void requireNonBlank(String response, String responseName) {
        if (response == null || response.isBlank()) {
            throw new BlockchainClientException(BlockchainClientError.MALFORMED_RESPONSE,
                    "SSAFY STO " + responseName + " response is empty");
        }
    }
}
