package com.aiblockclock.client.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecommendedAgent(
        @JsonProperty("agent_id") String agentId,
        String name,
        double score,
        String reason
) {
}
