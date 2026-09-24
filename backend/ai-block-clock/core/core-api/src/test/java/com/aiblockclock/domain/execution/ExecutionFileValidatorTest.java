package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionFileValidatorTest {
    private final ExecutionFileValidator validator = new ExecutionFileValidator();

    @ParameterizedTest
    @ValueSource(strings = {"성적.xlsx", "성적.XLSX", "성적.xls", "성적.csv"})
    @DisplayName("엑셀과 CSV 는 통과한다")
    void 엑셀과_CSV_는_통과한다(String filename) {
        assertThatCode(() -> validator.validate(file(filename, 1024))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"성적.pdf", "성적.txt", "성적.xlsx.exe", "성적"})
    @DisplayName("엑셀도 CSV 도 아니면 돈을 떼기 전에 튕긴다")
    void 엑셀도_CSV_도_아니면_튕긴다(String filename) {
        assertError(() -> validator.validate(file(filename, 1024)),
                ErrorType.EXECUTION_FILE_TYPE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("빈 파일은 받지 않는다")
    void 빈_파일은_받지_않는다() {
        assertError(() -> validator.validate(new UploadedFile("성적.csv", new byte[0])),
                ErrorType.EXECUTION_FILE_EMPTY);
    }

    @Test
    @DisplayName("10MB 를 넘으면 받지 않는다")
    void _10MB_를_넘으면_받지_않는다() {
        assertError(() -> validator.validate(file("성적.xlsx", 10 * 1024 * 1024 + 1)),
                ErrorType.EXECUTION_FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("10MB 는 통과한다")
    void _10MB_는_통과한다() {
        assertThatCode(() -> validator.validate(file("성적.xlsx", 10 * 1024 * 1024)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이름이 없으면 확장자를 알 수 없으므로 튕긴다")
    void 이름이_없으면_튕긴다() {
        assertError(() -> validator.validate(new UploadedFile(null, "점수".getBytes(StandardCharsets.UTF_8))),
                ErrorType.EXECUTION_FILE_TYPE_NOT_SUPPORTED);
    }

    private static UploadedFile file(String filename, int size) {
        return new UploadedFile(filename, new byte[size]);
    }

    private static void assertError(Runnable call, ErrorType expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(expected));
    }
}
