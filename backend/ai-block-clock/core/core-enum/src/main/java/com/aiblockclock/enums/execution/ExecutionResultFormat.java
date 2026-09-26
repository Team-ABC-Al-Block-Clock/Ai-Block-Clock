package com.aiblockclock.enums.execution;

import java.util.Locale;

public enum ExecutionResultFormat {
    JSON("json", "application/json"),
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String contentType;

    ExecutionResultFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public static ExecutionResultFormat defaultFormat() {
        return JSON;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public String filename() {
        return "result." + extension;
    }

    public String downloadName(String executionId) {
        return executionId.toLowerCase(Locale.ROOT) + "." + extension;
    }
}
