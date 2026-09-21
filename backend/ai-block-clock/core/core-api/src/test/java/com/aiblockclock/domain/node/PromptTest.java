package com.aiblockclock.domain.node;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n"})
    @DisplayName("비어 있는 프롬프트로는 노드를 만들 수 없다")
    void rejectsBlank(String value) {
        assertThatThrownBy(() -> Prompt.of(value))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NODE_PROMPT_EMPTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"당신은 분석가입니다.", " 앞뒤 공백은 그대로 둔다 "})
    @DisplayName("프롬프트 원문은 손대지 않고 그대로 담는다")
    void keepsValueAsIs(String value) {
        assertThat(Prompt.of(value).value()).isEqualTo(value);
    }
}
