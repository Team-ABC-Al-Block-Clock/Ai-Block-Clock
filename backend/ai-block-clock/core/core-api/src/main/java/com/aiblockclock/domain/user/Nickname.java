package com.aiblockclock.domain.user;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

// 화면도 앞뒤 공백을 지운 뒤 길이를 잰다. 같은 기준이어야 화면이 통과시킨 이름을 서버가 거절하지 않는다.
public record Nickname(String value) {
    private static final int MIN_LENGTH = 2;

    private static final int MAX_LENGTH = 20;

    public Nickname {
        value = value == null ? "" : value.strip();

        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.USER_NICKNAME_LENGTH_INVALID);
        }
    }

    public static Nickname of(String value) {
        return new Nickname(value);
    }
}
