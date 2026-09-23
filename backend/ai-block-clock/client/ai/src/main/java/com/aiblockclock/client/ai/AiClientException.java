package com.aiblockclock.client.ai;

import lombok.Getter;

@Getter
public class AiClientException extends RuntimeException {
    private final AiClientError error;

    public AiClientException(AiClientError error, String message) {
        super(message);
        this.error = error;
    }

    public AiClientException(AiClientError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }
}
