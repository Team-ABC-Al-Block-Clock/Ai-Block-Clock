package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.RefFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface PlannedNode {
    String id();

    Stream<PlannedPrompt> prompts();

    Map<String, Object> toRuntime(Map<String, List<RefFile>> refFiles);
}
