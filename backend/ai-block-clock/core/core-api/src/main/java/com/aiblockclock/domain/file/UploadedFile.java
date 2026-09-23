package com.aiblockclock.domain.file;

import java.util.Locale;

public record UploadedFile(String filename, byte[] content) {
    public int size() {
        return content == null ? 0 : content.length;
    }

    public String extension() {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');

        return dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
    }
}
