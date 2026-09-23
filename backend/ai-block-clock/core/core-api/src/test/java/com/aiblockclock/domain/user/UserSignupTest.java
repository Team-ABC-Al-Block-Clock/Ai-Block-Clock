package com.aiblockclock.domain.user;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSignupTest {
    private static final Nickname NICKNAME = Nickname.of("김싸피");

    private static final Introduction INTRODUCTION = Introduction.of("에이전트를 만듭니다");

    @Test
    void 두_동의가_모두_있으면_가입_내용을_만든다() {
        UserSignup signup = new UserSignup(NICKNAME, INTRODUCTION, true, true);

        assertThat(signup.nickname()).isEqualTo(NICKNAME);
        assertThat(signup.introduction()).isEqualTo(INTRODUCTION);
    }

    @Test
    void 약관에_동의하지_않으면_가입할_수_없다() {
        assertThatThrownBy(() -> new UserSignup(NICKNAME, INTRODUCTION, false, true))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_TERMS_NOT_AGREED));
    }

    @Test
    void 복구_불가_고지를_확인하지_않으면_가입할_수_없다() {
        assertThatThrownBy(() -> new UserSignup(NICKNAME, INTRODUCTION, true, false))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_RECOVERY_NOTICE_NOT_CONFIRMED));
    }
}
