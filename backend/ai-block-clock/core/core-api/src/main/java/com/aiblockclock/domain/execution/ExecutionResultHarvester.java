package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.enums.execution.ExecutionResultFormat;
import com.aiblockclock.storage.execution.ExecutionResultRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionResultHarvester {
    private final AiRuntimeClient aiRuntimeClient;
    private final ExecutionResultStore executionResultStore;
    private final ExecutionResultWriter executionResultWriter;
    private final ExecutionResultRepository executionResultRepository;

    public void harvest(Execution execution) {
        if (executionResultRepository.existsByExecutionId(execution.getId().value())) {
            return;
        }

        try {
            store(execution, ExecutionResultFormat.JSON,
                    aiRuntimeClient.readArtifact(execution.getRunId().value()).getBytes(StandardCharsets.UTF_8));

            aiRuntimeClient.readPdf(execution.getRunId().value())
                    .ifPresent(pdf -> store(execution, ExecutionResultFormat.PDF, pdf));
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
