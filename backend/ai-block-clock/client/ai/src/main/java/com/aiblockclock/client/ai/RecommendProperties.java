package com.aiblockclock.client.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "ai.recommend")
public record RecommendProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
    public RecommendProperties {
        AiEndpoint.check(baseUrl, "ai.recommend.base-url");
        AiEndpoint.checkTimeout(connectTimeout, "ai.recommend.connect-timeout");
        AiEndpoint.checkTimeout(readTimeout, "ai.recommend.read-timeout");
    }
}
