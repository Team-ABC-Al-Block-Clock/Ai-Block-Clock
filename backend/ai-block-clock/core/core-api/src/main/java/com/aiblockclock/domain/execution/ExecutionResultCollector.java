package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunResultVolume;
import com.aiblockclock.enums.execution.ExecutionResultFormat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

// 끝난 실행의 결과물을 결과 볼륨(ai/docs/integration-guide.md §4.5)에서 가져와 우리 file-store 에 보관한다. 엔진 HTTP 로 받지 않는다.
// 이미 보관한 형식은 다시 가져오지 않는다. 볼륨에 없는 형식은 건너뛴다.
// 가져오지 못해도 던지지 않는다. 실행은 이미 닫혔고, 받으러 올 때(download) 다시 가져온다.
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionResultCollector {
    private final RunResultVolume runResultVolume;
    private final ExecutionResultStore executionResultStore;
    private final ExecutionResultWriter executionResultWriter;
    private final ExecutionResultReader executionResultReader;

    public void collect(ExecutionId id) {
        List<ExecutionResultFormat> kept = executionResultReader.readFormats(id);

        try {
            if (!kept.contains(ExecutionResultFormat.JSON)) {
                collect(id, ExecutionResultFormat.JSON, runResultVolume::readArtifact);
            }

            if (!kept.contains(ExecutionResultFormat.PDF)) {
                collect(id, ExecutionResultFormat.PDF, runResultVolume::readPdf);
            }
        } catch (RuntimeException exception) {
            log.warn("실행 {} 의 결과물을 가져오지 못했다. 받으러 올 때 다시 가져온다: {}",
                    id.value(), exception.getMessage(), exception);
        }
    }

    private void collect(ExecutionId id, ExecutionResultFormat format, Function<String, Optional<byte[]>> volume) {
        volume.apply(id.value().toString()).ifPresent(content -> store(id, format, content));
    }

    // 기록을 남기지 못하면 옮긴 파일도 지운다. 기록 없는 파일은 아무도 찾지 않는다.
    private void store(ExecutionId id, ExecutionResultFormat format, byte[] content) {
        ExecutionResult result = executionResultStore.write(id, format, content);

        try {
            executionResultWriter.append(result);
        } catch (RuntimeException exception) {
            executionResultStore.erase(result);

            throw exception;
        }
    }
}
