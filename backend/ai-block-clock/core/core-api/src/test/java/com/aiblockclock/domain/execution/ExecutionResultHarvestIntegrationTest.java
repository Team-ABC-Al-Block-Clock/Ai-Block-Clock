package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.execution.ExecutionResultFormat;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.execution.ExecutionResultRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("integration")
@SpringBootTest
class ExecutionResultHarvestIntegrationTest {
    private static final UserId REQUESTER = UserId.of(9401L);
    private static final String RUN_ID = "20260924-100451-688";
    private static final String ARTIFACT = "{\"schema_id\":\"common.document@1\"}";

    @TempDir
    private static Path fileRoot;

    @DynamicPropertySource
    static void 결과는_임시_폴더에_쌓는다(DynamicPropertyRegistry registry) {
        registry.add("file-store.root", () -> fileRoot);
    }

    @MockitoBean
    private AiRuntimeClient aiRuntimeClient;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private ExecutionWriter executionWriter;

    @MockitoSpyBean
    private ExecutionResultReader executionResultReader;

    @Autowired
    private ExecutionResultWriter executionResultWriter;

    @Autowired
    private ExecutionResultRepository executionResultRepository;

    @Autowired
    private ExecutionResultStore executionResultStore;

    private ExecutionId executionId;

    @BeforeEach
    void setUp() {
        executionId = ExecutionId.newId();
        executionWriter.append(Execution.requested(executionId, ExecutionRequestId.of(UUID.randomUUID()),
                WorkflowId.newId(), REQUESTER, 2, null));
        executionWriter.markDispatched(executionId, RunId.of(RUN_ID));

        given(aiRuntimeClient.readArtifact(RUN_ID)).willReturn(ARTIFACT);
        given(aiRuntimeClient.readPdf(RUN_ID))
                .willReturn(Optional.of("%PDF-1.7".getBytes(StandardCharsets.UTF_8)));
    }

    @AfterEach
    void tearDown() {
        executionResultRepository.findByExecutionIdOrderByIdAsc(executionId.value())
                .forEach(entity -> executionResultStore.erase(ExecutionResultEntityConverter.toDomain(entity)));
        executionResultRepository.deleteAll(
                executionResultRepository.findByExecutionIdOrderByIdAsc(executionId.value()));
    }

    @Test
    @DisplayName("실행이 끝난 것을 본 순간 결과와 PDF 를 받아 보관한다")
    void harvestsOnFinish() {
        given(aiRuntimeClient.read(RUN_ID)).willReturn(succeeded());

        executionService.beginWatching(executionService.mine(executionId, REQUESTER));
        executionService.pollOnce(executionId);

        assertThat(executionResultReader.readFormats(executionId))
                .containsExactlyInAnyOrder(ExecutionResultFormat.JSON, ExecutionResultFormat.PDF);
    }

    @Test
    @DisplayName("폴링이 같은 상태를 여러 번 봐도 한 번만 받아 온다")
    void harvestsOnlyOnce() {
        given(aiRuntimeClient.read(RUN_ID)).willReturn(succeeded());

        executionService.follow(executionId, REQUESTER);
        executionService.follow(executionId, REQUESTER);
        executionService.follow(executionId, REQUESTER);

        verify(aiRuntimeClient, times(1)).readArtifact(RUN_ID);
    }

    @Test
    @DisplayName("아직 도는 중이면 받아 오지 않는다")
    void leavesRunningExecutionAlone() {
        given(aiRuntimeClient.read(RUN_ID)).willReturn(running());

        executionService.follow(executionId, REQUESTER);

        verify(aiRuntimeClient, never()).readArtifact(any());
        assertThat(executionResultReader.readFormats(executionId)).isEmpty();
    }

    @Test
    @DisplayName("PDF 렌더가 아직이면 결과 JSON 만 보관한다")
    void keepsJsonWhenPdfIsAbsent() {
        given(aiRuntimeClient.read(RUN_ID)).willReturn(succeeded());
        given(aiRuntimeClient.readPdf(RUN_ID)).willReturn(Optional.empty());

        executionService.follow(executionId, REQUESTER);

        assertThat(executionResultReader.readFormats(executionId))
                .containsExactly(ExecutionResultFormat.JSON);
    }

    @Test
    @DisplayName("PDF 만 빠졌으면 받으러 올 때 PDF 만 받아 온다")
    void retriesOnlyMissingPdf() {
        given(aiRuntimeClient.read(RUN_ID)).willReturn(succeeded());
        given(aiRuntimeClient.readPdf(RUN_ID)).willReturn(Optional.empty());
        executionService.follow(executionId, REQUESTER);

        willReturn(Optional.of("%PDF-1.7".getBytes(StandardCharsets.UTF_8))).given(aiRuntimeClient).readPdf(RUN_ID);
        ExecutionResult result = executionService.download(executionId, REQUESTER, ExecutionResultFormat.PDF);

        assertThat(executionService.locate(result)).exists().hasContent("%PDF-1.7");
        assertThat(executionResultReader.readFormats(executionId))
                .containsExactlyInAnyOrder(ExecutionResultFormat.JSON, ExecutionResultFormat.PDF);
        verify(aiRuntimeClient, times(1)).readArtifact(RUN_ID);
    }

    @Test
    @DisplayName("다른 쪽이 먼저 받아 두었으면 그 파일을 지우지 않는다")
    void keepsFileKeptByTheOtherSide() {
        ExecutionResult earlier = executionResultStore.write(
                executionId, ExecutionResultFormat.JSON, ARTIFACT.getBytes(StandardCharsets.UTF_8));
        executionResultWriter.append(earlier);
        willReturn(List.of()).given(executionResultReader).readFormats(executionId);
        given(aiRuntimeClient.read(RUN_ID)).willReturn(succeeded());

        executionService.follow(executionId, REQUESTER);

        assertThat(executionService.locate(earlier)).exists().hasContent(ARTIFACT);
        assertThat(executionResultRepository.findByExecutionIdOrderByIdAsc(executionId.value()))
                .extracting(entity -> entity.getFormat())
                .containsExactly(ExecutionResultFormat.JSON, ExecutionResultFormat.PDF);
    }

    @Test
    @DisplayName("받아 오다 실패해도 실행은 성공으로 남는다")
    void keepsExecutionSucceededWhenHarvestFails() {
        given(aiRuntimeClient.read(RUN_ID)).willReturn(succeeded());
        willThrow(new IllegalStateException("엔진이 끊겼다")).given(aiRuntimeClient).readArtifact(RUN_ID);

        ExecutionProgress progress = executionService.follow(executionId, REQUESTER);

        assertThat(progress.status()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(executionResultReader.readFormats(executionId)).isEmpty();
    }

    @Test
    @DisplayName("받아 두지 못했으면 받으러 올 때 다시 시도한다")
    void retriesWhenDownloadFindsNothing() {
        given(aiRuntimeClient.read(RUN_ID)).willReturn(succeeded());
        willThrow(new IllegalStateException("엔진이 끊겼다")).given(aiRuntimeClient).readArtifact(RUN_ID);
        executionService.follow(executionId, REQUESTER);

        willReturn(ARTIFACT).given(aiRuntimeClient).readArtifact(RUN_ID);
        ExecutionResult result = executionService.download(executionId, REQUESTER, ExecutionResultFormat.JSON);

        assertThat(result.format()).isEqualTo(ExecutionResultFormat.JSON);
        assertThat(executionService.locate(result)).exists().hasContent(ARTIFACT);
    }

    @Test
    @DisplayName("아직 끝나지 않은 실행의 결과는 받을 수 없다")
    void rejectsDownloadBeforeFinish() {
        assertThatThrownBy(() -> executionService.download(executionId, REQUESTER, ExecutionResultFormat.JSON))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.EXECUTION_RESULT_NOT_READY);
    }

    @Test
    @DisplayName("남의 실행 결과는 받을 수 없다")
    void rejectsDownloadByOthers() {
        given(aiRuntimeClient.read(RUN_ID)).willReturn(succeeded());
        executionService.follow(executionId, REQUESTER);

        assertThatThrownBy(() ->
                executionService.download(executionId, UserId.of(9402L), ExecutionResultFormat.JSON))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.EXECUTION_NOT_FOUND);
    }

    private static RunStatus succeeded() {
        return status("succeeded");
    }

    private static RunStatus running() {
        return status("running");
    }

    private static RunStatus status(String state) {
        return new RunStatus(RUN_ID, state, new RunStatus.Progress(2, 2, 100, null, null),
                List.of(), List.of(), List.of(), null, null, false, null);
    }
}
