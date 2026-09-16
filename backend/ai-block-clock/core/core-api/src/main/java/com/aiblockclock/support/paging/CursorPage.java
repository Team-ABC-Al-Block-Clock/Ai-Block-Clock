package com.aiblockclock.support.paging;

import java.util.List;

/**
 * 커서 페이지네이션 결과.
 *
 * <p>도메인마다 같은 모양의 페이지 레코드를 만들지 않도록 공통으로 둔다.
 *
 * @param content    이번 페이지의 내용
 * @param nextCursor 다음 페이지 커서. 다음 페이지가 없으면 {@code null}
 * @param hasNext    다음 페이지 존재 여부
 */
public record CursorPage<T>(List<T> content, String nextCursor, boolean hasNext) {
    public CursorPage {
        content = List.copyOf(content);
    }

    public static <T> CursorPage<T> last(List<T> content) {
        return new CursorPage<>(content, null, false);
    }
}
