package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.ExecutionProgressResponse;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionProgress;
import com.aiblockclock.domain.execution.ExecutionProgressListener;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

@Slf4j
@Component
public class ExecutionStreamBroadcaster implements ExecutionProgressListener {
    private static final Duration HOLD = Duration.ofHours(1);

    private final ConcurrentMap<ExecutionId, List<SseEmitter>> watching = new ConcurrentHashMap<>();

    public SseEmitter subscribe(ExecutionId id, Supplier<ExecutionProgress> current) {
        SseEmitter emitter = new SseEmitter(HOLD.toMillis());

        register(id, emitter);

        ExecutionProgress progress;

        try {
            progress = current.get();
        } catch (RuntimeException exception) {
            forget(id, emitter);

            throw exception;
        }

        send(emitter, progress);

        if (progress.status().isFinished()) {
            forget(id, emitter);
            emitter.complete();
        }

        return emitter;
    }

    @Override
    public void onProgress(ExecutionProgress progress) {
        List<SseEmitter> emitters = progress.status().isFinished()
                ? watching.remove(progress.id())
                : watching.get(progress.id());

        if (emitters == null) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            send(emitter, progress);

            if (progress.status().isFinished()) {
                emitter.complete();
            }
        }
    }

    private void register(ExecutionId id, SseEmitter emitter) {
        watching.compute(id, (key, emitters) -> {
            List<SseEmitter> registered = emitters == null ? new CopyOnWriteArrayList<>() : emitters;

            registered.add(emitter);

            return registered;
        });

        emitter.onCompletion(() -> forget(id, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitter.complete());
    }

    private void forget(ExecutionId id, SseEmitter emitter) {
        watching.computeIfPresent(id, (key, emitters) -> {
            emitters.remove(emitter);

            return emitters.isEmpty() ? null : emitters;
        });
    }

    private void send(SseEmitter emitter, ExecutionProgress progress) {
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(ExecutionProgressResponse.from(progress)));
        } catch (IOException | IllegalStateException exception) {
            log.debug("진행 상황을 보내지 못해 연결을 닫는다: execution={}", progress.id().value());
            emitter.complete();
        }
    }
}
