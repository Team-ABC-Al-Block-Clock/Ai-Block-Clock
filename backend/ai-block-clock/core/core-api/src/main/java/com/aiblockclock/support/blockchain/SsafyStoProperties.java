package com.aiblockclock.support.blockchain;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssafy.sto")
public record SsafyStoProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration transactionTimeout
) {
    public SsafyStoProperties {
        if (baseUrl == null || baseUrl.getHost() == null
                || !("https".equalsIgnoreCase(baseUrl.getScheme())
                || "http".equalsIgnoreCase(baseUrl.getScheme()))
                || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null) {
            throw new IllegalArgumentException("ssafy.sto.base-url must be an HTTP(S) server URI without credentials, query or fragment");
        }
        validateTimeout(connectTimeout, "ssafy.sto.connect-timeout");
        validateTimeout(readTimeout, "ssafy.sto.read-timeout");
        validateTimeout(transactionTimeout, "ssafy.sto.transaction-timeout");
    }

    private static void validateTimeout(Duration timeout, String propertyName) {
        if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0
                || timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException(propertyName + " must be between 1ms and " + Integer.MAX_VALUE + "ms");
        }
    }
}
