package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.enums.execution.ExecutionResultFormat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionResultHarvester {
    private final AiRuntimeClient aiRuntimeClient;
    private final ExecutionResultStore executionResultStore;
    private final ExecutionResultWriter executionResultWriter;
    private final ExecutionResultReader executionResultReader;

    public void harvest(Execution execution) {
        List<ExecutionResultFormat> kept = executionResultReader.readFormats(execution.getId());

        try {
            if (!kept.contains(ExecutionResultFormat.JSON)) {
                store(execution, ExecutionResultFormat.JSON, aiRuntimeClient.readArtifact(
                        execution.getRunId().value()).getBytes(StandardCharsets.UTF_8));
            }

            if (!kept.contains(ExecutionResultFormat.PDF)) {
                aiRuntimeClient.readPdf(execution.getRunId().value())
                        .ifPresent(pdf -> store(execution, ExecutionResultFormat.PDF, pdf));
            }
        } catch (RuntimeException exception) {
            log.warn("실행 {} 의 결과를 가져오지 못했다. 받으러 올 때 다시 시도한다: {}",
                    execution.getId().value(), exception.getMessage(), exception);
        }
    }

    private void store(Execution execution, ExecutionResultFormat format, byte[] content) {
        ExecutionResult result = executionResultStore.write(execution.getId(), format, content);

        try {
            executionResultWriter.append(result);
        } catch (RuntimeException exception) {
            executionResultStore.erase(result);

            throw exception;
        }
    }
}
