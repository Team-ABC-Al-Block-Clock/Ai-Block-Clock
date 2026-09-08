package com.aiblockclock.api.response;

public record ErrorResponse(
        String status,
        String code,
        String message
) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse("error", code, message);
    }
}