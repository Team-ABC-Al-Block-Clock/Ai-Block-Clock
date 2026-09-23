package com.aiblockclock.client.ai;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

public final class AiEndpoint {
    private AiEndpoint() {
    }

    public static void check(URI baseUrl, String propertyName) {
        if (baseUrl == null || baseUrl.getHost() == null
                || !("https".equalsIgnoreCase(baseUrl.getScheme())
                || "http".equalsIgnoreCase(baseUrl.getScheme()))
                || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null) {
            throw new AiClientException(AiClientError.INVALID_CONFIGURATION,
                    propertyName + " must be an HTTP(S) server URI without credentials, query or fragment");
        }
    }

    public static void checkTimeout(Duration timeout, String propertyName) {
        if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0
                || timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new AiClientException(AiClientError.INVALID_CONFIGURATION,
                    propertyName + " must be between 1ms and " + Integer.MAX_VALUE + "ms");
        }
    }

    public static RestClient restClient(URI baseUrl, Duration connectTimeout, Duration readTimeout,
                                        AiProperties shared) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl.toString())
                .requestFactory(requestFactory);

        if (shared.hasServiceToken()) {
            builder.defaultHeader(AiProperties.SERVICE_TOKEN_HEADER, shared.serviceToken());
        }

        return builder.build();
    }
}
