package com.aiblockclock.domain.user;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

// 소개는 선택이다. 비워 보내면 빈 문자열이 아니라 "없음"(null)으로 남겨 화면이 null 하나로만 판단하게 한다.
public record Introduction(String value) {
    private static final int MAX_LENGTH = 40;

    public Introduction {
        value = value == null || value.isBlank() ? null : value.strip();

        if (value != null && value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.USER_INTRODUCTION_TOO_LONG);
        }
    }

    public static Introduction of(String value) {
        return new Introduction(value);
    }
}
