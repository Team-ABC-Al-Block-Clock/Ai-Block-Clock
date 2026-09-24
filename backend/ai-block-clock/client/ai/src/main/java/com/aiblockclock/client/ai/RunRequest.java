package com.aiblockclock.client.ai;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record RunRequest(String workflow, String input, String comment, Path file,
                         Map<String, List<Path>> refFiles, boolean mock) {
    public RunRequest {
        refFiles = Map.copyOf(refFiles);
    }

    public static RunRequest of(String workflow, String comment, Path file, boolean mock) {
        return new RunRequest(workflow, null, comment, file, Map.of(), mock);
    }
}
