package com.aiblockclock.client.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

// 런타임이 succeeded 를 보내기 전에 결과물을 써 두는 공유 볼륨이다 (ai/docs/integration-guide.md §4.5).
//   <root>/<execution_id>/artifact.json   봉투. 성공이면 항상
//   <root>/<execution_id>/report.pdf      PDF 를 만들었을 때만
// 이벤트에 실린 경로는 믿지 않고 실행 번호로 고정 위치를 읽는다. 파일이 없으면 비어 있다.
// 실행 번호는 우리가 만든 UUID 라 경로 밖으로 풀리지 않는다.
public class RunResultVolume {
    private static final String ARTIFACT = "artifact.json";
    private static final String PDF = "report.pdf";

    private final Path root;

    public RunResultVolume(Path root) {
        this.root = root;
    }

    public Optional<byte[]> readArtifact(String executionId) {
        return read(executionId, ARTIFACT);
    }

    public Optional<byte[]> readPdf(String executionId) {
        return read(executionId, PDF);
    }

    // 볼륨을 읽지 못하는 것(권한 · 디스크)은 없는 것과 다르다. 부르는 쪽이 다음에 다시 읽도록 던진다.
    private Optional<byte[]> read(String executionId, String filename) {
        try {
            return Optional.of(Files.readAllBytes(root.resolve(executionId).resolve(filename)));
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        } catch (IOException exception) {
            throw new AiClientException(AiClientError.REQUEST_FAILED,
                    "reading " + filename + " of run " + executionId + " failed", exception);
        }
    }
}
