package com.aiblockclock.client.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(String serviceToken) {
    public static final String SERVICE_TOKEN_HEADER = "X-ABC-Service-Token";

    public boolean hasServiceToken() {
        return serviceToken != null && !serviceToken.isBlank();
    }
}
