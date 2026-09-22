package com.aiblockclock.support.paging;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * offset 을 base64 로 감싼 커서.
 *
 * <p>커서 모양을 하고 있지만 실제로는 offset 페이지네이션이다. 페이지를 넘기는 사이에 행이
 * 추가·삭제되면 창이 밀려 누락·중복이 생길 수 있다. 정렬 키가 유일한 목록은 keyset 커서로
 * 바꾸는 편이 낫다.
 */
public final class OffsetCursor {
    private OffsetCursor() {
    }

    public static String encode(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
    }

    public static int decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }

        try {
            int offset = Integer.parseInt(
                    new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (offset < 0) {
                throw new NumberFormatException(cursor);
            }

            return offset;
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.INVALID_REQUEST);
        }
    }
}
