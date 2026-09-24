package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.file.FileStore;
import com.aiblockclock.domain.file.FileStoreProperties;
import com.aiblockclock.domain.file.UploadedFile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionFileStoreTest {
    @TempDir
    private Path root;

    @Test
    @DisplayName("실행마다 제 폴더에 저장하고 확장자를 남긴다")
    void 실행마다_제_폴더에_저장한다() {
        ExecutionId id = ExecutionId.newId();

        ExecutionFile stored = store().write(id, upload("2학기 성적.xlsx"));

        assertThat(stored.key()).isEqualTo("executions/" + id.value() + "/input.xlsx");
        assertThat(stored.filename()).isEqualTo("2학기 성적.xlsx");
        assertThat(root.resolve(stored.key())).exists().hasContent("점수");
    }

    @Test
    @DisplayName("사용자가 지은 이름은 경로에 쓰지 않는다")
    void 사용자가_지은_이름은_경로에_쓰지_않는다() {
        ExecutionId id = ExecutionId.newId();

        ExecutionFile stored = store().write(id, upload("../../etc/passwd.csv"));

        assertThat(stored.key()).isEqualTo("executions/" + id.value() + "/input.csv");
        assertThat(root.resolve(stored.key())).exists();
    }

    @Test
    @DisplayName("지우면 실행 폴더까지 걷어간다")
    void 지우면_실행_폴더까지_걷어간다() {
        ExecutionFileStore store = store();
        ExecutionFile stored = store.write(ExecutionId.newId(), upload("성적.csv"));

        store.erase(stored);

        assertThat(root.resolve(stored.key())).doesNotExist();
        assertThat(root.resolve(stored.key()).getParent()).doesNotExist();
    }

    @Test
    @DisplayName("올린 파일이 없으면 지울 것도 없다")
    void 올린_파일이_없으면_지울_것도_없다() {
        assertThat(Files.exists(root)).isTrue();

        store().erase(null);
    }

    private ExecutionFileStore store() {
        return new ExecutionFileStore(new FileStore(new FileStoreProperties(root)));
    }

    private static UploadedFile upload(String filename) {
        return new UploadedFile(filename, "점수".getBytes(StandardCharsets.UTF_8));
    }
}
