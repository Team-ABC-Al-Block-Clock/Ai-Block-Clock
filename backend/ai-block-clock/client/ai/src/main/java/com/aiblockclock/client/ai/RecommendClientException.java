package com.aiblockclock.client.ai;

import lombok.Getter;

@Getter
public class RecommendClientException extends RuntimeException {
    private final RecommendClientError error;

    public RecommendClientException(RecommendClientError error, String message) {
        super(message);
        this.error = error;
    }

    public RecommendClientException(RecommendClientError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }
}
