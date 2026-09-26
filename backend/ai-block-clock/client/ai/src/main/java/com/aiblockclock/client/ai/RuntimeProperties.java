package com.aiblockclock.client.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "ai.runtime")
public record RuntimeProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
    public RuntimeProperties {
        AiEndpoint.check(baseUrl, "ai.runtime.base-url");
        AiEndpoint.checkTimeout(connectTimeout, "ai.runtime.connect-timeout");
        AiEndpoint.checkTimeout(readTimeout, "ai.runtime.read-timeout");
    }
}
