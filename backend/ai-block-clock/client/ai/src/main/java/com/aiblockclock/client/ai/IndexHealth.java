package com.aiblockclock.client.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IndexHealth(
        String status,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("indexed_agents") int indexedAgents
) {
    public boolean isEmpty() {
        return indexedAgents == 0;
    }
}
