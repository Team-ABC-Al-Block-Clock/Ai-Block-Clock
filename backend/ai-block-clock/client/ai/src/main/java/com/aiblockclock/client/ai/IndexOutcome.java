package com.aiblockclock.client.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IndexOutcome(
        String op,
        int indexed,
        int total,
        @JsonProperty("model_version") String modelVersion
) {
}
