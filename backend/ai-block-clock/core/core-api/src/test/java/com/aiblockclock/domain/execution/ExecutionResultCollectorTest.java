package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunResultVolume;
import com.aiblockclock.domain.file.FileStore;
import com.aiblockclock.domain.file.FileStoreProperties;
import com.aiblockclock.enums.execution.ExecutionResultFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

// 끝난 실행의 결과물은 런타임이 결과 볼륨에 써 둔다(contract 5장). 엔진 HTTP(/artifact · /pdf)로 받지 않는다.
// 볼륨에서 읽은 것을 우리 file-store 에 옮기고 결과 기록을 남긴다 — 받기(download)는 우리 쪽 파일을 내려준다.
class ExecutionResultCollectorTest {
    private static final String ARTIFACT = "{\"schema_id\":\"common.document@1\"}";
    private static final byte[] PDF = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);

    @TempDir
    private Path fileRoot;

    private final ExecutionId id = ExecutionId.newId();
    private final RunResultVolume volume = mock(RunResultVolume.class);
    private final ExecutionResultWriter writer = mock(ExecutionResultWriter.class);
    private final ExecutionResultReader reader = mock(ExecutionResultReader.class);

    private ExecutionResultCollector sut;

    @BeforeEach
    void 비어_있는_보관소를_둔다() {
        sut = new ExecutionResultCollector(volume, new ExecutionResultStore(new FileStore(new FileStoreProperties(fileRoot))),
                writer, reader);
        given(reader.readFormats(id)).willReturn(List.of());
    }

    @Test
    void 볼륨의_결과물과_PDF_를_우리_보관소로_옮기고_기록을_남긴다() {
        given(volume.readArtifact(id.value().toString())).willReturn(Optional.of(ARTIFACT.getBytes(StandardCharsets.UTF_8)));
        given(volume.readPdf(id.value().toString())).willReturn(Optional.of(PDF));

        sut.collect(id);

        assertThat(fileRoot.resolve("executions/" + id.value() + "/result.json")).hasContent(ARTIFACT);
        assertThat(fileRoot.resolve("executions/" + id.value() + "/result.pdf")).hasBinaryContent(PDF);
        assertThat(appended()).extracting(ExecutionResult::executionId, ExecutionResult::format, ExecutionResult::size)
                .containsExactlyInAnyOrder(
                        tuple(id, ExecutionResultFormat.JSON, (long) ARTIFACT.length()),
                        tuple(id, ExecutionResultFormat.PDF, (long) PDF.length));
    }

    @Test
    void PDF_를_만들지_않은_실행은_결과물만_옮긴다() {
        given(volume.readArtifact(id.value().toString())).willReturn(Optional.of(ARTIFACT.getBytes(StandardCharsets.UTF_8)));
        given(volume.readPdf(id.value().toString())).willReturn(Optional.empty());

        sut.collect(id);

        assertThat(appended()).extracting(ExecutionResult::format).containsExactly(ExecutionResultFormat.JSON);
        assertThat(fileRoot.resolve("executions/" + id.value() + "/result.pdf")).doesNotExist();
    }

    @Test
    void 이미_보관한_형식은_볼륨에서_다시_가져오지_않는다() {
        given(reader.readFormats(id)).willReturn(List.of(ExecutionResultFormat.JSON));
        given(volume.readPdf(id.value().toString())).willReturn(Optional.of(PDF));

        sut.collect(id);

        then(volume).should(never()).readArtifact(any());
        assertThat(appended()).extracting(ExecutionResult::format).containsExactly(ExecutionResultFormat.PDF);
    }

    @Test
    void 두_형식을_모두_보관했으면_볼륨을_읽지_않는다() {
        given(reader.readFormats(id)).willReturn(List.of(ExecutionResultFormat.JSON, ExecutionResultFormat.PDF));

        sut.collect(id);

        then(volume).shouldHaveNoInteractions();
        then(writer).shouldHaveNoInteractions();
    }

    @Test
    void 볼륨에_결과물이_없으면_아무것도_남기지_않는다() {
        given(volume.readArtifact(id.value().toString())).willReturn(Optional.empty());
        given(volume.readPdf(id.value().toString())).willReturn(Optional.empty());

        sut.collect(id);

        then(writer).shouldHaveNoInteractions();
        assertThat(fileRoot.resolve("executions")).doesNotExist();
    }

    // 기록 없는 파일은 아무도 찾지 않는다. 실행은 이미 닫혔으니 던지지 않고, 받으러 올 때 다시 가져온다.
    @Test
    void 결과_기록을_남기지_못하면_옮긴_파일을_지우고_던지지_않는다() {
        given(volume.readArtifact(id.value().toString())).willReturn(Optional.of(ARTIFACT.getBytes(StandardCharsets.UTF_8)));
        willThrow(new IllegalStateException("db down")).given(writer).append(any());

        assertThatCode(() -> sut.collect(id)).doesNotThrowAnyException();

        assertThat(fileRoot.resolve("executions/" + id.value() + "/result.json")).doesNotExist();
    }

    private List<ExecutionResult> appended() {
        ArgumentCaptor<ExecutionResult> results = ArgumentCaptor.forClass(ExecutionResult.class);

        then(writer).should(atLeastOnce()).append(results.capture());

        return results.getAllValues();
    }
}
