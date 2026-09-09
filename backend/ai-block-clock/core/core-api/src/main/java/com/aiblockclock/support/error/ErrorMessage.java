package com.aiblockclock.support.error;

import lombok.Getter;

@Getter
public class ErrorMessage {
    private final String code;
    private final String message;
    private final Object data;

    public ErrorMessage(ErrorType errorType, Object data) {
        this.code = errorType.getCode().name();
        this.message = errorType.getMessage();
        this.data = data;
    }
}
