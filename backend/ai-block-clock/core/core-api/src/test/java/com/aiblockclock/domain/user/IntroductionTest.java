package com.aiblockclock.domain.user;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntroductionTest {
    @Test
    void 앞뒤_공백을_지운_소개를_담는다() {
        assertThat(Introduction.of("  에이전트를 만듭니다  ").value()).isEqualTo("에이전트를 만듭니다");
    }

    @Test
    void 사십_자까지는_소개로_쓸_수_있다() {
        assertThat(Introduction.of("가".repeat(40)).value()).isEqualTo("가".repeat(40));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void 비어_있으면_소개가_없는_것으로_둔다(String value) {
        assertThat(Introduction.of(value).value()).isNull();
    }

    @Test
    void 공백을_지우고_사십_자를_넘으면_소개로_쓸_수_없다() {
        assertThatThrownBy(() -> Introduction.of("가".repeat(41)))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_INTRODUCTION_TOO_LONG));
    }
}
