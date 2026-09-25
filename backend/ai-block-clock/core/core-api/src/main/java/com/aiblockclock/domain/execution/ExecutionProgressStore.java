package com.aiblockclock.domain.execution;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ExecutionProgressStore {
    private final ConcurrentMap<ExecutionId, ExecutionProgress> latest = new ConcurrentHashMap<>();

    public void put(ExecutionProgress progress) {
        latest.put(progress.id(), progress);
    }

    public Optional<ExecutionProgress> find(ExecutionId id) {
        return Optional.ofNullable(latest.get(id));
    }

    public void forget(ExecutionId id) {
        latest.remove(id);
    }
}
