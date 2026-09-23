package com.aiblockclock.domain.user;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknameTest {
    @ParameterizedTest
    @ValueSource(strings = {"김싸", "김싸피", "가나다라마바사아자차카타파하가나다라마바"})
    void 두_자부터_스무_자까지는_이름으로_쓸_수_있다(String value) {
        assertThat(Nickname.of(value).value()).isEqualTo(value);
    }

    @Test
    void 앞뒤_공백을_지운_이름을_담는다() {
        assertThat(Nickname.of("  김싸피  ").value()).isEqualTo("김싸피");
    }

    @Test
    void 공백을_지운_뒤_길이를_잰다() {
        assertThat(Nickname.of(" " + "가".repeat(20) + " ").value()).isEqualTo("가".repeat(20));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "김", " 김 ", "가나다라마바사아자차카타파하가나다라마바사"})
    void 공백을_지우고_두_자_미만이거나_스무_자를_넘으면_이름으로_쓸_수_없다(String value) {
        assertThatThrownBy(() -> Nickname.of(value))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NICKNAME_LENGTH_INVALID));
    }
}
