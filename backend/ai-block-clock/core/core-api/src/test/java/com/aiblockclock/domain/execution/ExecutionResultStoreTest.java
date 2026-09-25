package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.file.FileStore;
import com.aiblockclock.domain.file.FileStoreProperties;
import com.aiblockclock.enums.execution.ExecutionResultFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionResultStoreTest {
    @TempDir
    private Path root;

    @Test
    @DisplayName("올린 파일과 같은 폴더에 형식마다 따로 둔다")
    void 형식마다_따로_둔다() {
        ExecutionId id = ExecutionId.newId();
        ExecutionResultStore store = store();

        ExecutionResult json = store.write(id, ExecutionResultFormat.JSON, bytes("{}"));
        ExecutionResult pdf = store.write(id, ExecutionResultFormat.PDF, bytes("%PDF-1.7"));

        assertThat(json.key()).isEqualTo("executions/" + id.value() + "/result.json");
        assertThat(pdf.key()).isEqualTo("executions/" + id.value() + "/result.pdf");
        assertThat(root.resolve(json.key())).exists().hasContent("{}");
        assertThat(root.resolve(pdf.key())).exists();
    }

    @Test
    @DisplayName("크기를 함께 남겨 내려줄 때 길이로 쓴다")
    void 크기를_함께_남긴다() {
        ExecutionResult stored = store().write(ExecutionId.newId(), ExecutionResultFormat.JSON, bytes("{\"a\":1}"));

        assertThat(stored.size()).isEqualTo(7);
    }

    @Test
    @DisplayName("지우면 파일이 남지 않는다")
    void 지우면_남지_않는다() {
        ExecutionResultStore store = store();
        ExecutionResult stored = store.write(ExecutionId.newId(), ExecutionResultFormat.PDF, bytes("%PDF-1.7"));

        store.erase(stored);

        assertThat(root.resolve(stored.key())).doesNotExist();
    }

    private ExecutionResultStore store() {
        return new ExecutionResultStore(new FileStore(new FileStoreProperties(root)));
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
