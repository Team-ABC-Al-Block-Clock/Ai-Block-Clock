package com.aiblockclock.client.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "ai.recommend")
public record RecommendProperties(
        URI baseUrl,
        String serviceToken,
        Duration connectTimeout,
        Duration readTimeout
) {
    public RecommendProperties {
        if (baseUrl == null || baseUrl.getHost() == null
                || !("https".equalsIgnoreCase(baseUrl.getScheme())
                || "http".equalsIgnoreCase(baseUrl.getScheme()))
                || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null) {
            throw new RecommendClientException(RecommendClientError.INVALID_CONFIGURATION,
                    "ai.recommend.base-url must be an HTTP(S) server URI without credentials, query or fragment");
        }

        validateTimeout(connectTimeout, "ai.recommend.connect-timeout");
        validateTimeout(readTimeout, "ai.recommend.read-timeout");
    }

    public boolean hasServiceToken() {
        return serviceToken != null && !serviceToken.isBlank();
    }

    private static void validateTimeout(Duration timeout, String propertyName) {
        if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0
                || timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new RecommendClientException(RecommendClientError.INVALID_CONFIGURATION,
                    propertyName + " must be between 1ms and " + Integer.MAX_VALUE + "ms");
        }
    }
}
