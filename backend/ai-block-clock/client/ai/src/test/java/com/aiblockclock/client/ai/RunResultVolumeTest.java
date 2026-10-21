package com.aiblockclock.client.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// 결과 볼륨의 고정 위치(contract 5장)를 실행 번호로 읽는다. 이벤트에 실린 경로는 쓰지 않는다.
class RunResultVolumeTest {
    private static final String EXECUTION_ID = "6f1c2a9e-0000-4000-8000-000000000001";
    private static final String ARTIFACT = "{\"schema_id\":\"common.document@1\"}";

    @TempDir
    private Path root;

    @Test
    void 실행_번호_폴더의_artifact_json_을_결과물로_읽는다() throws IOException {
        write(EXECUTION_ID + "/artifact.json", ARTIFACT);

        assertThat(new RunResultVolume(root).readArtifact(EXECUTION_ID))
                .hasValueSatisfying(bytes -> assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(ARTIFACT));
    }

    @Test
    void 실행_번호_폴더의_report_pdf_를_PDF_로_읽는다() throws IOException {
        write(EXECUTION_ID + "/report.pdf", "%PDF-1.7");

        assertThat(new RunResultVolume(root).readPdf(EXECUTION_ID))
                .hasValueSatisfying(bytes -> assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("%PDF-1.7"));
    }

    @Test
    void PDF_를_만들지_않은_실행은_PDF_가_없다() throws IOException {
        write(EXECUTION_ID + "/artifact.json", ARTIFACT);

        assertThat(new RunResultVolume(root).readPdf(EXECUTION_ID)).isEmpty();
    }

    @Test
    void 결과_폴더가_없는_실행은_결과물도_없다() {
        assertThat(new RunResultVolume(root).readArtifact(EXECUTION_ID)).isEmpty();
    }

    @Test
    void 다른_실행의_결과물은_읽지_않는다() throws IOException {
        write("7a2d3b0f-0000-4000-8000-000000000002/artifact.json", ARTIFACT);

        assertThat(new RunResultVolume(root).readArtifact(EXECUTION_ID)).isEmpty();
    }

    private void write(String relative, String content) throws IOException {
        Path path = root.resolve(relative);

        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
