package com.aiblockclock.client.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Recommendations(
        List<RecommendedAgent> recommendations,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("latency_ms") int latencyMs
) {
}
