package com.aiblockclock.client.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunAccepted(
        @JsonProperty("run_id") String runId,
        String status,
        @JsonProperty("node_count") Integer nodeCount,
        @JsonProperty("poll_interval_ms") Integer pollIntervalMs
) {
}
