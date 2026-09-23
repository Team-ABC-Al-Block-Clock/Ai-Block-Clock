package com.aiblockclock.domain.node;

import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefFileValidatorTest {
    private static final long MB = 1024 * 1024;

    private final RefFileValidator validator = new RefFileValidator();

    @ParameterizedTest
    @ValueSource(strings = {"기준표.pdf", "설명.txt", "설명.md", "표.csv", "예시.png", "예시.jpg", "예시.JPEG"})
    @DisplayName("계약이 받는 형식은 통과한다")
    void 계약이_받는_형식은_통과한다(String filename) {
        assertThatCode(() -> validator.validate(file(filename, 1024), List.of()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"기준표.xlsx", "기준표.docx", "기준표.zip", "기준표"})
    @DisplayName("계약에 없는 형식은 붙일 때 막는다")
    void 계약에_없는_형식은_막는다(String filename) {
        assertError(() -> validator.validate(file(filename, 1024), List.of()),
                ErrorType.REF_FILE_TYPE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("빈 파일은 붙이지 않는다")
    void 빈_파일은_붙이지_않는다() {
        assertError(() -> validator.validate(new UploadedFile("기준표.pdf", new byte[0]), List.of()),
                ErrorType.REF_FILE_EMPTY);
    }

    @Test
    @DisplayName("하나씩은 작아도 합계가 10MB 를 넘으면 막는다")
    void 합계가_10MB_를_넘으면_막는다() {
        List<RefFile> attached = List.of(
                new RefFile(RefFileId.newId(), "nodes/a/1.pdf", "1.pdf", 6 * MB),
                new RefFile(RefFileId.newId(), "nodes/a/2.pdf", "2.pdf", 3 * MB));

        assertError(() -> validator.validate(file("3.pdf", (int) (2 * MB)), attached),
                ErrorType.REF_FILE_TOTAL_TOO_LARGE);
    }

    @Test
    @DisplayName("합계가 딱 10MB 면 통과한다")
    void 합계가_딱_10MB_면_통과한다() {
        List<RefFile> attached = List.of(
                new RefFile(RefFileId.newId(), "nodes/a/1.pdf", "1.pdf", 9 * MB));

        assertThatCode(() -> validator.validate(file("2.pdf", (int) MB), attached))
                .doesNotThrowAnyException();
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
