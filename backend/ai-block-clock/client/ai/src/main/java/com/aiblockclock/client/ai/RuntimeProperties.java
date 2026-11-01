package com.aiblockclock.client.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

// resultsRoot 는 런타임과 나눠 쓰는 결과 볼륨의 루트다(ai.runtime.results-root, ai/docs/integration-guide.md §4.5).
@ConfigurationProperties(prefix = "ai.runtime")
public record RuntimeProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout, Path resultsRoot) {
    public RuntimeProperties {
        AiEndpoint.check(baseUrl, "ai.runtime.base-url");
        AiEndpoint.checkTimeout(connectTimeout, "ai.runtime.connect-timeout");
        AiEndpoint.checkTimeout(readTimeout, "ai.runtime.read-timeout");
    }
}
